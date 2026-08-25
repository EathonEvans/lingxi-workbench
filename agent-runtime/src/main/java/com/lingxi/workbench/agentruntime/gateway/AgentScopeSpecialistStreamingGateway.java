package com.lingxi.workbench.agentruntime.gateway;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeInterruptionRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillCatalog;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillMiddleware;
import com.lingxi.workbench.agentruntime.skill.AgentScopeSkillLoadAuditor;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeChatModelResource;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeOpenAiChatModelFactory;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** AgentScope ReAct 的唯一 Specialist 入口，统一承载纯文本、Tool 和 ASK/Confirm 恢复。 */
@Component
public class AgentScopeSpecialistStreamingGateway {

    private final AgentScopeOpenAiChatModelFactory chatModelFactory;
    private final ToolApprovalFingerprint approvalFingerprint;

    public AgentScopeSpecialistStreamingGateway(AgentScopeOpenAiChatModelFactory chatModelFactory,
                                                ToolApprovalFingerprint approvalFingerprint) {
        this.chatModelFactory = chatModelFactory;
        this.approvalFingerprint = approvalFingerprint;
    }

    /** 执行一次新的 Specialist Agent 调用。 */
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command, ExecutionState state,
                                         RuntimeContext runtimeContext, AgentScopePublishedSkillCatalog skillCatalog) {
        Objects.requireNonNull(command, "AgentScope模型命令不能为空");
        Objects.requireNonNull(state, "AgentScope执行状态不能为空");
        Objects.requireNonNull(runtimeContext, "AgentScope运行上下文不能为空");
        Objects.requireNonNull(skillCatalog, "AgentScope发布Skill目录不能为空");
        return Flux.defer(() -> execute(command, state, runtimeContext, skillCatalog, null, null, List.of()));
    }

    /** 以 PostgreSQL 中锁定的 AgentState 与批准 Tool 调用恢复同一运行。 */
    public Flux<ChatStreamChunk> resume(ChatStreamCommand command, ExecutionState state,
                                        RuntimeContext runtimeContext, AgentScopePublishedSkillCatalog skillCatalog,
                                        AgentScopeCheckpoint checkpoint, Long interactionId, String fingerprint) {
        Objects.requireNonNull(checkpoint, "AgentScope检查点不能为空");
        Objects.requireNonNull(runtimeContext, "AgentScope运行上下文不能为空");
        Objects.requireNonNull(skillCatalog, "AgentScope发布Skill目录不能为空");
        state.ensureResumable();
        AgentScopeCheckpointStore store = Objects.requireNonNull(state.checkpointStore(),
                "AgentScope恢复必须提供审批存储器");
        AgentScopeApprovedInvocation approvedInvocation = store.claimApproved(checkpoint, interactionId, fingerprint);
        state.humanResolved(RuntimeInterruption.builder()
                .interactionId(interactionId)
                .fingerprint(fingerprint)
                .expiresAt(checkpoint.getExpiresAt())
                .build());
        return Flux.defer(() -> execute(command, state, runtimeContext, skillCatalog, checkpoint,
                approvedInvocation,
                confirmation(checkpoint)));
    }

    private Flux<ChatStreamChunk> execute(ChatStreamCommand command, ExecutionState state, RuntimeContext context,
                                          AgentScopePublishedSkillCatalog skillCatalog,
                                          AgentScopeCheckpoint checkpoint,
                                          AgentScopeApprovedInvocation approved,
                                          List<Msg> input) {
        AgentScopeChatModelResource modelResource = chatModelFactory.create(command.modelConfig());
        context.put(ExecutionState.class, state);
        if (approved != null) {
            context.put(AgentScopeApprovedInvocation.class, approved);
        }
        if (checkpoint == null) {
            skillCatalog.eagerBindings().forEach(binding -> state.recordSkillActivation(binding,
                    skillCatalog.eagerReason(binding)));
        }
        AgentScopeSkillLoadAuditor skillLoadAuditor = new AgentScopeSkillLoadAuditor(skillCatalog, state);
        ReActAgent agent;
        try {
            agent = agent(command, state, skillCatalog, checkpoint, context, modelResource, skillLoadAuditor);
        } catch (RuntimeException exception) {
            modelResource.close();
            return Flux.error(exception);
        }
        return agent.streamEvents(input, context)
                .doOnNext(event -> {
                    handleHumanConfirmation(event, state, context);
                })
                .ofType(TextBlockDeltaEvent.class)
                .map(TextBlockDeltaEvent::getDelta)
                .filter(delta -> delta != null && !delta.isEmpty())
                .map(ChatStreamChunk::new)
                .doOnCancel(() -> agent.interrupt(context))
                .doFinally(ignored -> close(agent, modelResource));
    }

    private ReActAgent agent(ChatStreamCommand command, ExecutionState state,
                             AgentScopePublishedSkillCatalog skillCatalog,
                             AgentScopeCheckpoint checkpoint, RuntimeContext runtimeContext,
                             AgentScopeChatModelResource modelResource,
                             AgentScopeSkillLoadAuditor skillLoadAuditor) {
        Toolkit toolkit = new Toolkit(ToolkitConfig.builder().parallel(false).build());
        if (state.hasTools()) {
            state.toolRuntimeConfig().getTools().forEach(tool -> toolkit.registerAgentTool(
                    new AgentScopeRuntimeTool(tool, state.toolRuntimeConfig().getExecutionContext(),
                            state.toolRuntimeConfig().getMaxResultCharacters(), state)));
        }
        if (checkpoint == null && runtimeContext.getAgentState() != null) {
            // 自定义 StateStore 返回初始 AgentState 时，AgentScope 不会重新合并 Builder 权限；
            // 必须把已发布的 Permission 策略显式写入该状态，避免默认策略放行审批 Tool。
            runtimeContext.getAgentState().setPermissionContext(permissionContext(state));
            if (skillCatalog.hasVisibleSkills()) {
                List<String> activeGroups = new ArrayList<>(runtimeContext.getAgentState().getToolContext()
                        .getActivatedGroups());
                if (!activeGroups.contains(AgentScopePublishedSkillMiddleware.SKILL_LOADER_GROUP)) {
                    activeGroups.add(AgentScopePublishedSkillMiddleware.SKILL_LOADER_GROUP);
                    runtimeContext.getAgentState().getToolContext().setActivatedGroups(activeGroups);
                }
            }
        }
        return ReActAgent.builder()
                .name("lingxi-specialist-" + state.getAgentVersionId())
                .sysPrompt(command.systemPrompt())
                .model(modelResource.model())
                .toolkit(toolkit)
                .maxIters(Math.max(state.remainingModelCalls(), 1))
                .permissionContext(permissionContext(state))
                .stateStore(prepareStateStore(runtimeContext, checkpoint))
                .enablePendingToolRecovery(false)
                .middleware(new AgentScopeRuntimeModelMiddleware())
                .middleware(new AgentScopePublishedSkillMiddleware(skillCatalog))
                .middleware(skillLoadAuditor)
                .dynamicSkillsEnabled(false)
                .build();
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
            stateStore.save(userId, sessionId, "agent_state",
                    AgentState.fromJsonString(checkpoint.getAgentStateJson()));
            return stateStore;
        }
        if (initialState != null && stateStore.get(userId, sessionId, "agent_state", AgentState.class).isEmpty()) {
            stateStore.save(userId, sessionId, "agent_state", initialState);
        }
        return stateStore;
    }

    private PermissionContextState permissionContext(ExecutionState state) {
        PermissionContextState.Builder builder = PermissionContextState.builder();
        if (!state.hasTools()) {
            return builder.build();
        }
        state.toolRuntimeConfig().getTools().stream()
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

    private void handleHumanConfirmation(Object event, ExecutionState state, RuntimeContext context) {
        if (!(event instanceof RequireUserConfirmEvent confirmation)) {
            return;
        }
        List<ToolUseBlock> calls = confirmation.getToolCalls();
        if (calls.size() != 1) {
            throw new IllegalArgumentException("一次AgentScope人工确认只能包含一个Tool调用");
        }
        AgentScopeCheckpoint checkpointSeed = Objects.requireNonNull(state.checkpointSeed(),
                "需要人工审批的Agent调用必须提供检查点上下文");
        AgentScopeCheckpointStore store = Objects.requireNonNull(state.checkpointStore(),
                "需要人工审批的Agent调用必须提供检查点存储器");
        ToolUseBlock call = calls.getFirst();
        AgentToolDefinition definition = state.requireToolDefinition(call.getName());
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(definition.getSecurityPolicy().getApprovalTtlSeconds());
        String input = JSON.toJSONString(call.getInput());
        String configurationHash = approvalFingerprint.configHash(definition);
        String fingerprint = approvalFingerprint.approval(state.getRunId(), state.getAgentVersionId(),
                definition.getCode(), configurationHash, input,
                definition.getSecurityPolicy().getResourceScope(), expiresAt);
        AgentScopeCheckpoint checkpoint = AgentScopeCheckpoint.builder()
                .version(AgentScopeCheckpoint.CURRENT_VERSION)
                .query(checkpointSeed.getQuery())
                .capabilityCode(checkpointSeed.getCapabilityCode())
                .applicationVersionId(checkpointSeed.getApplicationVersionId())
                .userId(checkpointSeed.getUserId())
                .conversationId(checkpointSeed.getConversationId())
                .assistantMessageId(checkpointSeed.getAssistantMessageId())
                .explicitSkillCodes(checkpointSeed.getExplicitSkillCodes())
                .applicationUsageBeforeAgent(checkpointSeed.getApplicationUsageBeforeAgent())
                .applicationModelCallsBeforeAgent(checkpointSeed.getApplicationModelCallsBeforeAgent())
                .applicationToolCallsBeforeAgent(checkpointSeed.getApplicationToolCallsBeforeAgent())
                .runId(state.getRunId())
                .agentId(state.getAgentId())
                .agentVersionId(state.getAgentVersionId())
                .maxModelCalls(state.getPolicy().getMaxModelCalls())
                .maxToolCalls(state.getPolicy().getMaxToolCalls())
                .maxTotalTokens(state.getPolicy().getMaxTotalTokens())
                .maxFinalizationTokens(state.getPolicy().getMaxFinalizationTokens())
                .modelCalls(state.modelCalls())
                .toolCalls(state.toolCalls())
                .toolCallsInCurrentModelRound(state.toolCallsInCurrentModelRound())
                .tokenUsage(state.tokenUsage())
                .lastSequenceNo(state.lastSequenceNo())
                .finalizationRequested(state.isFinalizationRequested())
                .finalizationReason(state.finalizationReason())
                .output(state.output())
                .toolCallCounts(state.checkpointToolCallCounts())
                .authorizedToolCodes(state.checkpointAuthorizedToolCodes())
                .agentStateJson(Objects.requireNonNull(context.getAgentState(),
                        "AgentScope ASK缺少AgentState").toJson())
                .pendingToolCall(new AgentScopeCheckpoint.ToolCallSnapshot(call.getId(), call.getName(),
                        new java.util.LinkedHashMap<>(call.getInput())))
                .toolConfigurationHash(configurationHash)
                .resourceScope(definition.getSecurityPolicy().getResourceScope())
                .fingerprint(fingerprint)
                .expiresAt(expiresAt)
                .build();
        RuntimeInterruption interruption = store.interrupt(new AgentScopeInterruptionRequest(checkpoint,
                definition, state.toolRuntimeConfig().getExecutionContext()));
        state.interrupt(interruption);
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

    private static void close(ReActAgent agent, AgentScopeChatModelResource modelResource) {
        try {
            agent.close();
        } finally {
            modelResource.close();
        }
    }
}
