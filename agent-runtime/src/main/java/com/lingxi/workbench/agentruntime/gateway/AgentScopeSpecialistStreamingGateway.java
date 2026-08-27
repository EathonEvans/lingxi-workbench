package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeInterruptionRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionInterruption;
import com.lingxi.workbench.agentruntime.model.ExecutionOutput;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillCatalog;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillMiddleware;
import com.lingxi.workbench.agentruntime.skill.AgentScopeSkillLoadAuditor;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeChatModelResource;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeOpenAiChatModelFactory;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AgentScope Harness 的 Specialist 入口，统一承载纯文本、Tool 和 ASK/Confirm 恢复。
 */
@Component
public class AgentScopeSpecialistStreamingGateway {

    private static final String HARNESS_ASYNC_RESULT_TOOL = "wait_async_results";

    private final AgentScopeOpenAiChatModelFactory chatModelFactory;
    private final ToolApprovalFingerprint approvalFingerprint;

    public AgentScopeSpecialistStreamingGateway(AgentScopeOpenAiChatModelFactory chatModelFactory,
                                                ToolApprovalFingerprint approvalFingerprint) {
        this.chatModelFactory = chatModelFactory;
        this.approvalFingerprint = approvalFingerprint;
    }

    /**
     * 执行一次新的 Specialist Agent 调用。
     */
    public Flux<ChatStreamChunk> stream(AgentRunRequest request, RuntimeContext runtimeContext,
                                        AgentScopePublishedSkillCatalog skillCatalog) {
        ChatStreamCommand command = request.getModelCommand();
        Objects.requireNonNull(command, "AgentScope模型命令不能为空");
        Objects.requireNonNull(runtimeContext, "AgentScope运行上下文不能为空");
        Objects.requireNonNull(skillCatalog, "AgentScope发布Skill目录不能为空");
        return Flux.defer(() -> execute(request, runtimeContext, skillCatalog, null, null, List.of()));
    }

    /**
     * 以 PostgreSQL 中锁定的 AgentState 与批准 Tool 调用恢复同一运行。
     */
    public Flux<ChatStreamChunk> resume(AgentRunRequest request, RuntimeContext runtimeContext,
                                        AgentScopePublishedSkillCatalog skillCatalog,
                                        AgentScopeCheckpoint checkpoint, Long interactionId, String fingerprint) {
        Objects.requireNonNull(checkpoint, "AgentScope检查点不能为空");
        Objects.requireNonNull(runtimeContext, "AgentScope运行上下文不能为空");
        Objects.requireNonNull(skillCatalog, "AgentScope发布Skill目录不能为空");
        ExecutionEvents events = require(runtimeContext, ExecutionEvents.class);
        if (events.isCancelled()) {
            throw new com.lingxi.workbench.agentruntime.model.AgentRunCancelledException();
        }
        AgentScopeCheckpointStore store = Objects.requireNonNull(request.getCheckpointStore(), "AgentScope恢复必须提供审批存储器");
        AgentScopeApprovedInvocation approvedInvocation = store.claimApproved(checkpoint, interactionId, fingerprint);
        events.humanResolved(RuntimeInterruption.builder()
                .interactionId(interactionId)
                .fingerprint(fingerprint)
                .expiresAt(checkpoint.getExpiresAt())
                .build());
        return Flux.defer(() -> execute(request, runtimeContext, skillCatalog, checkpoint,
                approvedInvocation,
                confirmation(checkpoint)));
    }

    private Flux<ChatStreamChunk> execute(AgentRunRequest request, RuntimeContext context,
                                          AgentScopePublishedSkillCatalog skillCatalog,
                                          AgentScopeCheckpoint checkpoint,
                                          AgentScopeApprovedInvocation approved,
                                          List<Msg> input) {
        ChatStreamCommand command = request.getModelCommand();
        ExecutionTools tools = require(context, ExecutionTools.class);
        ExecutionEvents events = require(context, ExecutionEvents.class);
        AgentScopeChatModelResource modelResource = chatModelFactory.create(command.modelConfig());
        if (approved != null) {
            context.put(AgentScopeApprovedInvocation.class, approved);
        }
        if (checkpoint == null) {
            skillCatalog.eagerBindings().forEach(binding -> {
                tools.validateSkillTools(binding.getEffectiveToolCodes(), binding.getSkill().getCode());
                events.skillActivated(binding, skillCatalog.eagerReason(binding));
            });
        }
        AgentScopeSkillLoadAuditor skillLoadAuditor = new AgentScopeSkillLoadAuditor(skillCatalog, tools, events);
        HarnessAgent agent;
        try {
            agent = agent(request, skillCatalog, checkpoint, context, modelResource, skillLoadAuditor);
        } catch (RuntimeException exception) {
            modelResource.close();
            return Flux.error(exception);
        }
        return agent.streamEvents(input, context)
                .doOnNext(event -> {
                    handleHumanConfirmation(event, request, context,
                            agent.getDelegate().getAgentState(context));
                })
                .filter(event -> Objects.isNull(event.getSource()))
                .ofType(AgentResultEvent.class)
                .map(event -> event.getResult().getTextContent())
                .filter(content -> content != null && !content.isBlank())
                .map(ChatStreamChunk::new)
                .doOnCancel(agent::interrupt)
                .doFinally(ignored -> close(agent, modelResource));
    }

    private HarnessAgent agent(AgentRunRequest request,
                             AgentScopePublishedSkillCatalog skillCatalog,
                             AgentScopeCheckpoint checkpoint, RuntimeContext runtimeContext,
                             AgentScopeChatModelResource modelResource,
                             AgentScopeSkillLoadAuditor skillLoadAuditor) {
        ChatStreamCommand command = request.getModelCommand();
        ExecutionBudget budget = require(runtimeContext, ExecutionBudget.class);
        ExecutionTools tools = require(runtimeContext, ExecutionTools.class);
        Toolkit toolkit = new Toolkit(ToolkitConfig.builder().parallel(false).build());
        if (tools.hasTools()) {
            tools.runtimeConfig()
                    .getTools()
                    .forEach(tool -> toolkit.registerAgentTool(new AgentScopeRuntimeTool(tool,
                            tools.runtimeConfig().getExecutionContext(),
                            tools.runtimeConfig().getMaxResultCharacters(), request.getCheckpointStore())));
        }
        if (checkpoint == null && runtimeContext.getAgentState() != null) {
            // 自定义 StateStore 返回初始 AgentState 时，AgentScope 不会重新合并 Builder 权限；
            // 必须把已发布的 Permission 策略显式写入该状态，避免默认策略放行审批 Tool。
            runtimeContext.getAgentState().setPermissionContext(permissionContext(tools));
            if (skillCatalog.hasVisibleSkills()) {
                List<String> activeGroups = new ArrayList<>(runtimeContext.getAgentState().getToolContext()
                        .getActivatedGroups());
                if (!activeGroups.contains(AgentScopePublishedSkillMiddleware.SKILL_LOADER_GROUP)) {
                    activeGroups.add(AgentScopePublishedSkillMiddleware.SKILL_LOADER_GROUP);
                    runtimeContext.getAgentState().getToolContext().setActivatedGroups(activeGroups);
                }
            }
        }
        HarnessAgent agent = HarnessAgent.builder()
                .name("lingxi-specialist-" + request.getAgentVersionId())
                .sysPrompt(command.systemPrompt())
                .model(modelResource.model())
                .toolkit(toolkit)
                // HITL 恢复会先重放一次已批准 Tool 的 acting，该迭代不应挤占剩余模型调用槽位。
                .maxIters(Math.max(budget.remainingModelCalls() + (checkpoint == null ? 0 : 1), 1))
                .permissionContext(permissionContext(tools))
                .stateStore(prepareStateStore(runtimeContext, checkpoint))
                .enablePendingToolRecovery(false)
                .middleware(new AgentScopeRuntimeModelMiddleware())
                .middleware(new AgentScopePublishedSkillMiddleware(skillCatalog))
                .middleware(skillLoadAuditor)
                // Lingxi 只开放发布版本显式授权的 Tool/Skill，关闭 Harness 的工作区侧能力。
                .disableCompaction()
                .disableToolResultEviction()
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableDynamicSubagents()
                .disableToolsConfig()
                .build();
        // Harness 2.0.2 即使禁用 Subagent 仍会注册异步结果工具；Lingxi 未发布该能力，必须移除。
        agent.getToolkit().removeTool(HARNESS_ASYNC_RESULT_TOOL);
        return agent;
    }

    /**
     * 将本次运行初态先写入共享 StateStore，再交给 AgentScope 按会话加载与保存。
     *
     * <p>普通运行只在对应 runId 尚无状态时写入初态；HITL 恢复以 PostgreSQL 中经校验的
     * Checkpoint 覆盖 Redis 运行态，避免缓存漂移影响已批准 Tool 的精确恢复。</p>
     */
    private static AgentStateStore prepareStateStore(RuntimeContext runtimeContext, AgentScopeCheckpoint checkpoint) {
        AgentStateStore stateStore = Objects.requireNonNull(runtimeContext.get(AgentStateStore.class),
                "AgentScope运行必须提供Redis StateStore");
        String userId = Objects.requireNonNull(runtimeContext.getUserId(), "AgentScope运行用户不能为空");
        String sessionId = Objects.requireNonNull(runtimeContext.getSessionId(), "AgentScope运行会话不能为空");
        AgentState initialState = runtimeContext.getAgentState();
        if (checkpoint != null) {
            AgentState checkpointState = AgentState.fromJsonString(checkpoint.getAgentStateJson());
            if (!Objects.equals(checkpointState.getSessionId(), sessionId)) {
                throw new IllegalArgumentException("AgentScope Checkpoint会话与恢复请求不一致");
            }
            stateStore.save(userId, sessionId, "agent_state", checkpointState);
            return stateStore;
        }
        if (initialState != null && stateStore.get(userId, sessionId, "agent_state", AgentState.class).isEmpty()) {
            stateStore.save(userId, sessionId, "agent_state", initialState);
        }
        return stateStore;
    }

    private PermissionContextState permissionContext(ExecutionTools tools) {
        PermissionContextState.Builder builder = PermissionContextState.builder();
        if (!tools.hasTools()) {
            return builder.build();
        }
        tools.runtimeConfig().getTools().stream()
                .map(tool -> tool.definition())
                .forEach(definition -> {
                    PermissionBehavior behavior = definition.getSecurityPolicy().isApprovalRequired()
                            ? PermissionBehavior.ASK : PermissionBehavior.ALLOW;
                    PermissionRule rule = new PermissionRule(definition.getCode(), null, behavior,
                            "lingxi-published-policy");
                    if (behavior == PermissionBehavior.ASK) {
                        builder.addAskRule(definition.getCode(), rule);
                        return;
                    }
                    builder.addAllowRule(definition.getCode(), rule);
                });
        return builder.build();
    }

    private void handleHumanConfirmation(Object event, AgentRunRequest request, RuntimeContext context,
                                         AgentState agentState) {
        if (!(event instanceof RequireUserConfirmEvent confirmation)) {
            return;
        }
        List<ToolUseBlock> calls = confirmation.getToolCalls();
        if (calls.size() != 1) {
            throw new IllegalArgumentException("一次AgentScope人工确认只能包含一个Tool调用");
        }
        ExecutionTools tools = require(context, ExecutionTools.class);
        ExecutionEvents events = require(context, ExecutionEvents.class);
        ExecutionInterruption interruptionSignal = require(context, ExecutionInterruption.class);
        AgentScopeCheckpoint checkpointSeed = Objects.requireNonNull(request.getCheckpointSeed(),
                "需要人工审批的Agent调用必须提供检查点上下文");
        AgentScopeCheckpointStore store = Objects.requireNonNull(request.getCheckpointStore(),
                "需要人工审批的Agent调用必须提供检查点存储器");
        ToolUseBlock call = calls.getFirst();
        AgentToolDefinition definition = tools.requireDefinition(call.getName());
        AgentScopeCheckpoint checkpoint = createCheckpoint(request, context, agentState,
                checkpointSeed, call, definition);
        RuntimeInterruption interruption = store.interrupt(new AgentScopeInterruptionRequest(checkpoint, definition,
                tools.runtimeConfig().getExecutionContext()));
        interruptionSignal.set(interruption);
        events.humanRequired(interruption);
    }

    private AgentScopeCheckpoint createCheckpoint(AgentRunRequest request, RuntimeContext context,
                                                   AgentState agentState,
                                                   AgentScopeCheckpoint checkpointSeed, ToolUseBlock call,
                                                   AgentToolDefinition definition) {
        ExecutionBudget budget = require(context, ExecutionBudget.class);
        ExecutionTools tools = require(context, ExecutionTools.class);
        ExecutionEvents events = require(context, ExecutionEvents.class);
        ExecutionOutput output = require(context, ExecutionOutput.class);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(definition.getSecurityPolicy().getApprovalTtlSeconds());
        String input = JSON.toJSONString(call.getInput());
        String configurationHash = approvalFingerprint.configHash(definition);
        String fingerprint = approvalFingerprint.approval(request.getRunId(), request.getAgentVersionId(),
                definition.getCode(), configurationHash, input,
                definition.getSecurityPolicy().getResourceScope(), expiresAt);
        AgentScopeCheckpoint.AgentScopeCheckpointBuilder builder = checkpointSeed.toBuilder()
                .version(AgentScopeCheckpoint.CURRENT_VERSION)
                .runId(request.getRunId())
                .agentSessionId(Objects.requireNonNull(context.getSessionId(),
                        "AgentScope ASK缺少会话标识"))
                .agentId(request.getAgentId())
                .agentVersionId(request.getAgentVersionId());
        captureBudget(builder, budget, tools);
        captureExecutionState(builder, agentState, events, output, tools);
        captureApproval(builder, call, definition, configurationHash, fingerprint, expiresAt);
        return builder.build();
    }

    private static void captureBudget(AgentScopeCheckpoint.AgentScopeCheckpointBuilder builder,
                                      ExecutionBudget budget, ExecutionTools tools) {
        builder.maxModelCalls(budget.policy().getMaxModelCalls())
                .maxToolCalls(budget.policy().getMaxToolCalls())
                .maxTotalTokens(budget.policy().getMaxTotalTokens())
                .maxFinalizationTokens(budget.policy().getMaxFinalizationTokens())
                .modelCalls(budget.modelCalls())
                .toolCalls(budget.toolCalls())
                .toolCallsInCurrentModelRound(tools.callsInCurrentModelRound())
                .tokenUsage(budget.tokenUsage())
                .finalizationRequested(budget.isFinalizationRequested())
                .finalizationReason(budget.finalizationReason());
    }

    private static void captureExecutionState(AgentScopeCheckpoint.AgentScopeCheckpointBuilder builder,
                                              AgentState agentState, ExecutionEvents events,
                                              ExecutionOutput output, ExecutionTools tools) {
        builder.lastSequenceNo(events.lastSequenceNo())
                .output(output.content())
                .toolCallCounts(tools.checkpointCallCounts())
                .authorizedToolCodes(tools.checkpointAuthorizedToolCodes())
                .agentStateJson(Objects.requireNonNull(agentState,
                        "AgentScope ASK缺少AgentState").toJson());
    }

    private static void captureApproval(AgentScopeCheckpoint.AgentScopeCheckpointBuilder builder,
                                        ToolUseBlock call, AgentToolDefinition definition,
                                        String configurationHash, String fingerprint,
                                        OffsetDateTime expiresAt) {
        builder.pendingToolCall(new AgentScopeCheckpoint.ToolCallSnapshot(call.getId(), call.getName(),
                        new java.util.LinkedHashMap<>(call.getInput())))
                .toolConfigurationHash(configurationHash)
                .resourceScope(definition.getSecurityPolicy().getResourceScope())
                .fingerprint(fingerprint)
                .expiresAt(expiresAt);
    }

    private static <T> T require(RuntimeContext context, Class<T> type) {
        T value = context.get(type);
        if (value == null) {
            throw new IllegalStateException("AgentScope调用缺少可信" + type.getSimpleName());
        }
        return value;
    }

    private static List<Msg> confirmation(AgentScopeCheckpoint checkpoint) {
        AgentScopeCheckpoint.ToolCallSnapshot call = checkpoint.getPendingToolCall();
        ToolUseBlock toolCall = new ToolUseBlock(call.id(), call.name(), call.input(),
                JSON.toJSONString(call.input()), null);
        return List.of(Msg.builderForRole(MsgRole.USER)
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS,
                        List.of(new ConfirmResult(true, toolCall))))
                .build());
    }

    private static void close(HarnessAgent agent, AgentScopeChatModelResource modelResource) {
        try {
            agent.close();
        } finally {
            modelResource.close();
        }
    }
}
