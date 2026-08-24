package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.FallbackTarget;
import com.lingxi.workbench.application.runtime.model.ExecutionResumeContext;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.InteractionResolution;
import com.lingxi.workbench.application.runtime.model.InteractionResumeTarget;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.service.agent.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.capability.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.routing.IntentRoutingService;
import com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ConversationContextPrepareBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.service.AiStageObserver;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Application Chat 主执行流程。
 *
 * <h3>执行顺序</h3>
 * <ol>
 *   <li>准备不可变 ConversationContext（窗口记忆、摘要、长期记忆、运行时变量）</li>
 *   <li>解析平台级交互会话（新建或恢复中的澄清/确认）</li>
 *   <li>意图路由（交互恢复时跳过）→ 候选召回 + 意图决策</li>
 *   <li>FIXED_RESPONSE / CLARIFICATION → 不调模型，直接返回</li>
 *   <li>意图路由转交或不适合直达时，进入 Application 锁定的 fallback Agent</li>
 *   <li>全部 AGENT/WORKFLOW 目标统一走 CapabilityExecutionService</li>
 *   <li>持久化终态和结果；Workflow与Agent上下文均通过显式参数传递</li>
 * </ol>
 *
 * <h3>关键设计</h3>
 * <ul>
 *   <li><b>能力目录</b>：ApplicationDefinition.capabilityCatalog 统一管理所有可执行能力，
 *       意图路由和默认入口都从同一目录解析，避免分散的 agentId/versionId 引用不一致</li>
 *   <li><b>CapabilityExecutionService</b>：AGENT 和 WORKFLOW 两种能力类型统一执行入口，
 *       内部根据类型分发到 AgentChat 或 WorkflowExecutor</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationChatExecutionServiceImpl implements ApplicationChatExecutionService {

    private static final String BUDGET_BOUNDARY_RESPONSE =
            "本轮上下文处理已达到预算边界，请缩小问题范围或开启新对话后继续。";

    /**
     * 统一能力执行入口，内部按 AGENT / WORKFLOW 类型分发。
     */
    private final CapabilityExecutionService capabilityExecutionService;
    /**
     * 会话上下文准备：窗口记忆 + 摘要 + 长期记忆 + 运行时变量。
     */
    private final ConversationContextService conversationContextService;
    /**
     * 意图路由：候选召回 + 语义决策 + 服务端映射校验。
     */
    private final IntentRoutingService intentRoutingService;
    /**
     * 平台级跨请求交互会话恢复。
     */
    private final InteractionSessionService interactionSessionService;
    /**
     * 预解析 Agent 能力，用于确定执行模式并避免执行层重复查询版本。
     */
    private final AgentRuntimeService agentRuntimeService;
    /**
     * 将主流程稳定阶段写入 OpenTelemetry，内部模型 span 自动继承当前阶段。
     */
    private final AiStageObserver aiStageObserver;
    /** 所有执行模式共用的顶级运行状态。 */
    private final ExecutionPersistenceService executionPersistenceService;
    /** 恢复时只重建可信基础变量，不重新加载记忆、重写问题或路由。 */
    private final RuntimeVariablesInitializer runtimeVariablesInitializer;

    /**
     * 先准备共享上下文，再尝试意图路由，其他请求统一交给 fallback Agent。
     *
     * <p>默认入口和意图路由统一通过发布快照中的能力目录执行，共享同一份可信对话上下文，
     * 并由能力调用显式传递本次执行上下文。</p>
     */
    @Override
    public ApplicationChatResult execute(ApplicationChatExecutionRequest request) {
        // ① 校验服务端组装的可信请求，避免不完整数据进入上下文准备和路由阶段
        requireRequest(request);
        Instant startedAt = Instant.now();
        AppRuntimeSnapshot snapshot = request.getApplication();

        // ② 一次性准备窗口记忆、摘要、长期记忆和运行时变量，后续路由与执行共享同一上下文
        ConversationContext preparedContext = request.getResumeContext() == null
                ? aiStageObserver.observe(AiObservationStage.CONTEXT_PREPARE,
                        () -> conversationContextService.prepare(ConversationContextPrepareBO.builder()
                                .application(snapshot)
                                .query(request.getQuery())
                                .loginUser(request.getLoginUser())
                                .turn(request.getTurn())
                                .build()))
                : restoreConversationContext(request);

        // ③ 平台恢复上一轮澄清或确认，锁定回到原 capability 与 AgentVersion。
        Optional<InteractionResolution> interaction = request.getResumeContext() == null
                ? interactionSessionService.resolve(snapshot, request.getTurn(), request.getLoginUser().id(),
                        preparedContext.getOriginalQuery(), request.getClarificationResponse())
                : Optional.empty();

        // 澄清会话内容补充，无需澄清则无需对原始问题进行补充
        ConversationContext context = interaction
                .map(resolution -> preparedContext.copyAndAppendClarificationSup(resolution.getOriginalQuery(),resolution.getResponse()))
                .orElse(preparedContext);

        // ④ 恢复请求跳过新路由；其他请求按 Application 配置执行意图路由。
        IntentRoutingResult routing = aiStageObserver.observe(AiObservationStage.INTENT_ROUTE,  ApplicationChatExecutionServiceImpl::recordRoutingEvidence,
                () -> routeIntent(request, context, interaction.isPresent() || request.getResumeContext() != null));

        ConversationContext routedContext = context.plusPreparationUsage(routing.getTokenUsage(), routing.getModelCalls());

        // ⑤ 调度合并和目标执行统一归入EXECUTION阶段，固定回复与澄清也保留完整链路。
        return aiStageObserver.observe(AiObservationStage.EXECUTION, () -> {
            ChatDispatch dispatch = request.getResumeContext() == null
                    ? this.dispatch(snapshot, routedContext, interaction, routing)
                    : resolveHitlDispatch(snapshot, routedContext.routingQuery(), request.getResumeContext());
            return switch (dispatch) {
                case ChatDispatch.Fixed fixed -> directResult(fixed.response(), routedContext);
                case ChatDispatch.Clarify clarify -> clarificationResult(request, clarify, routedContext);
                case ChatDispatch.Executable executable -> executeExecutable(request, routedContext, executable, startedAt);
            };
        });
    }

    /** Checkpoint已经保存模型消息历史，恢复时不得重复读取记忆、改写问题或重新路由。 */
    private ConversationContext restoreConversationContext(ApplicationChatExecutionRequest request) {
        RuntimeCheckpoint checkpoint = request.getResumeContext().getCheckpoint();
        if (checkpoint == null || checkpoint.getVersion() == null
                || checkpoint.getVersion() != RuntimeCheckpoint.CURRENT_VERSION
                || checkpoint.getContext() == null || checkpoint.getState() == null
                || !Objects.equals(checkpoint.getContext().getApplicationVersionId(),
                        request.getApplication().versionId())
                || !Objects.equals(checkpoint.getContext().getUserId(), request.getLoginUser().id())
                || !Objects.equals(checkpoint.getContext().getConversationId(),
                        request.getTurn().conversationId())
                || !Objects.equals(checkpoint.getContext().getAssistantMessageId(),
                        request.getTurn().assistantMessageId())
                || !Objects.equals(List.copyOf(Objects.requireNonNullElseGet(
                        checkpoint.getContext().getExplicitSkillCodes(), List::of)),
                        request.getExplicitSkillCodes())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Runtime Checkpoint与当前恢复请求不一致");
        }
        String query = checkpoint.getContext().getQuery();
        RuntimeVariables variables = runtimeVariablesInitializer.initialize(
                request.getLoginUser(), query, request.getTurn().conversationId());
        variables.put(com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors.SESSION_HISTORY,
                List.of());
        TokenUsage usageBeforeAgent = Objects.requireNonNullElseGet(
                checkpoint.getContext().getApplicationUsageBeforeAgent(), TokenUsage::empty);
        TokenUsage runtimeUsage = Objects.requireNonNullElseGet(
                checkpoint.getState().getTokenUsage(), TokenUsage::empty);
        int modelCalls = Objects.requireNonNullElse(
                checkpoint.getContext().getApplicationModelCallsBeforeAgent(), 0)
                + Objects.requireNonNullElse(checkpoint.getState().getModelCalls(), 0);
        return ConversationContext.builder()
                .applicationSnapshot(request.getApplication())
                .loginUser(request.getLoginUser())
                .turn(request.getTurn())
                .originalQuery(query)
                .rewrittenQuery(query)
                .recentMessages(List.of())
                .summary("")
                .longTermMemory("")
                .budget(request.getApplication().definition().contextPolicy().budget())
                .runtimeVariables(variables)
                .preparationTokenUsage(usageBeforeAgent.plus(runtimeUsage))
                .preparationModelCalls(modelCalls)
                .build();
    }

    private static int initialToolCalls(ApplicationChatExecutionRequest request) {
        ExecutionResumeContext resume = request.getResumeContext();
        if (resume == null) {
            return 0;
        }
        RuntimeCheckpoint checkpoint = resume.getCheckpoint();
        int beforeAgent = Objects.requireNonNullElse(
                checkpoint.getContext().getApplicationToolCallsBeforeAgent(), 0);
        int selectedCalls = Objects.requireNonNullElse(checkpoint.getState().getToolCalls(), 0);
        // 待批准Tool已经占用Agent局部额度，但尚未发布TOOL_STARTED，Application不能提前重复占用。
        return beforeAgent + Math.max(0, selectedCalls - 1);
    }

    /** HITL 恢复必须回到检查点锁定的 AgentVersion，不重新经过意图路由。 */
    private ChatDispatch.Executable resolveHitlDispatch(AppRuntimeSnapshot snapshot, String query,
                                                        ExecutionResumeContext resume) {
        RuntimeCheckpoint checkpoint = resume.getCheckpoint();
        ApplicationCapabilityDefinition capability = resolveCapability(
                snapshot, checkpoint.getContext().getCapabilityCode(), ApplicationCapabilityType.AGENT);
        if (!Objects.equals(capability.getTargetId(), checkpoint.getState().getAgentId())
                || !Objects.equals(capability.getTargetVersionId(), checkpoint.getState().getAgentVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "HITL恢复目标与ApplicationVersion不一致");
        }
        return capabilityDispatch(query, capability, null, Set.of());
    }

    /**
     * 将路由决策证据（门控原因 + 候选编码与相似度）记录到 INTENT_ROUTE span，
     * 供意图树质量分析与阈值调优。
     */
    private static void recordRoutingEvidence(io.micrometer.observation.Observation observation,
                                              IntentRoutingResult routing) {
        IntentRoutingEvidence evidence = routing.getRoutingEvidence();
        if (Objects.isNull(evidence)) {
            return;
        }
        observation.lowCardinalityKeyValue("lingxi.intent.reason", evidence.getReason().getCode());
        observation.lowCardinalityKeyValue("lingxi.intent.action", routing.getAction().getCode());
        observation.lowCardinalityKeyValue(
                "lingxi.intent.candidate.count", String.valueOf(evidence.candidates().size()));
    }

    /**
     * 将路由结果和Application策略合并为唯一、完整的最终调度决策。
     */
    private ChatDispatch dispatch(AppRuntimeSnapshot snapshot, ConversationContext context,
                                  Optional<InteractionResolution> interaction, IntentRoutingResult routing) {
        String executionQuery = resolveExecutionQuery(context, routing);
        if (interaction.isPresent()) {
            return resolveResumedDispatch(snapshot, executionQuery, interaction.get());
        }
        return switch (routing.getAction()) {
            case FIXED_RESPONSE -> new ChatDispatch.Fixed(routing.getResponse());
            case CLARIFICATION -> new ChatDispatch.Clarify(routing.getResponse(), routing.getClarification());
            case EXECUTE_AGENT -> capabilityDispatch(executionQuery,
                    resolveIntentRoutingCapability(snapshot, routing.getMapping(), ApplicationCapabilityType.AGENT),
                    null, Set.of());
            case EXECUTE_WORKFLOW -> capabilityDispatch(executionQuery,
                    resolveIntentRoutingCapability(snapshot, routing.getMapping(), ApplicationCapabilityType.WORKFLOW),
                    null, Set.of());
            case SKIPPED, DEFER_TO_AGENT -> resolveFallbackDispatch(
                    snapshot, executionQuery, routing.getRoutingEvidence(), Set.of());
        };
    }

    /**
     * 意图路由未执行能力时，统一进入 Application 锁定的 fallback Agent 能力。
     */
    private ChatDispatch.Executable resolveFallbackDispatch(AppRuntimeSnapshot snapshot, String query,
                                                            IntentRoutingEvidence evidence,
                                                            Set<String> confirmedCapabilityCodes) {
        FallbackTarget fallback = resolveFallback(snapshot);
        return new ChatDispatch.Capability(query, fallback.capability(),
                new CapabilityDispatchHints(fallback.agent(), evidence, confirmedCapabilityCodes));
    }

    private ChatDispatch.Executable resolveResumedDispatch(
            AppRuntimeSnapshot snapshot, String query, InteractionResolution interaction) {
        InteractionResumeTarget target = interaction.getResumeTarget();
        if (target == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "交互会话缺少恢复目标");
        }
        ApplicationCapabilityDefinition capability = resolveCapability(
                snapshot, target.capabilityCode(), ApplicationCapabilityType.AGENT);
        if (!Objects.equals(capability.getTargetId(), target.agentId())
                || !Objects.equals(capability.getTargetVersionId(), target.agentVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "交互会话恢复目标与ApplicationVersion不一致");
        }
        ChatDispatch.Capability dispatch = capabilityDispatch(query, capability, null, interaction.confirmedCapabilityCodes());
        if (dispatch.hints().agent().definition().getRole() != AgentRole.SUPERVISOR) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "交互会话恢复目标不是主Agent");
        }
        return dispatch;
    }

    private ChatDispatch.Capability capabilityDispatch(
            String query,
            ApplicationCapabilityDefinition capability,
            IntentRoutingEvidence evidence,
            Set<String> confirmedCapabilityCodes) {
        AgentRuntimeSnapshot agent = capability.getType() == ApplicationCapabilityType.AGENT
                ? agentRuntimeService.resolveVersion(capability.getTargetId(), capability.getTargetVersionId())
                : null;
        return new ChatDispatch.Capability(query, capability,
                new CapabilityDispatchHints(agent, evidence, confirmedCapabilityCodes));
    }

    private static ApplicationChatResult directResult(String response, ConversationContext context) {
        return ApplicationChatResult.completed(response, context.getPreparationTokenUsage(), List.of(), null);
    }

    private static ApplicationChatResult clarificationResult(ApplicationChatExecutionRequest request, ChatDispatch.Clarify clarify, ConversationContext context) {
        request.getEventPublisher().publishClarification(clarify.clarification());
        return directResult(clarify.response(), context);
    }

    /**
     * 为需要实际执行的调度统一管理运行上下文注册、日志和清理。
     */
    private ApplicationChatResult executeExecutable(ApplicationChatExecutionRequest request, ConversationContext conversationContext, ChatDispatch.Executable dispatch, Instant startedAt) {
        AppRuntimeSnapshot snapshot = request.getApplication();

        // ① 正常执行创建新 runId；HITL 恢复继续使用检查点锁定的原 runId。
        ExecutionResumeContext resume = request.getResumeContext();
        String executionId = resume == null ? IdUtil.getSnowflakeNextIdStr()
                : resume.getCheckpoint().getState().getRunId();
        ChatDispatch.Capability capabilityDispatch = (ChatDispatch.Capability) dispatch;
        validateExplicitSkillTarget(request, capabilityDispatch);
        if (resume == null) {
            executionPersistenceService.startRun(ExecutionRunStartCommand.builder()
                    .application(snapshot)
                    .turn(request.getTurn())
                    .userId(request.getLoginUser().id())
                    .runId(executionId)
                    .orchestratorAgent(capabilityDispatch.hints().agent())
                    .capabilityCode(capabilityDispatch.capability().getCode())
                    .executionMode(executionMode(dispatch))
                    .build());
        } else {
            executionPersistenceService.changeRunStatus(
                    executionId, ExecutionRunStatus.RESUMING, ExecutionRunStatus.RUNNING, "EXECUTION");
        }

        // ② 基于最终执行问题派生运行时变量，并构建本轮唯一的可写执行上下文
        ChatExecutionContext executionContext = createExecutionContext(
                request, conversationContext, executionId, dispatch.query());

        try {
            ApplicationChatResult result = aiStageObserver.observeExecution(executionId, executionMode(dispatch),
                    () -> executionContext.hasFinalizationCapacity()
                            ? executeCapability((ChatDispatch.Capability) dispatch, executionContext)
                            : budgetBoundaryResult(executionContext));

            if (result.isInterrupted()) {
                return result;
            }
            // ⑤ 统一记录最终执行类型、轨迹数量和完整链路耗时
            log.info("Application目标执行完成，applicationCode={}，versionId={}，executionId={}，targetType={}，traceCount={}，durationMs={}",
                    snapshot.applicationCode(), snapshot.versionId(), executionId, targetType(dispatch),
                    result.traces().size(), Duration.between(startedAt, Instant.now()).toMillis());
            executionPersistenceService.changeRunStatus(
                    executionId, ExecutionRunStatus.RUNNING, ExecutionRunStatus.SUCCEEDED, "TERMINAL");
            return result;
        } catch (RuntimeException exception) {
            if (hasCause(exception, AgentRunCancelledException.class)
                    || (hasInterruptedCause(exception) && executionContext.isCancelled())) {
                executionPersistenceService.changeRunStatus(
                        executionId, ExecutionRunStatus.RUNNING, ExecutionRunStatus.CANCELLED, "TERMINAL");
            } else if (!hasCause(exception, AgentReconciliationPendingException.class)) {
                executionPersistenceService.changeRunStatus(
                        executionId, ExecutionRunStatus.RUNNING, ExecutionRunStatus.FAILED, "TERMINAL");
            }
            boolean expectedTerminal = hasInterruptedCause(exception)
                    || hasCause(exception, AgentRunCancelledException.class);
            if (!expectedTerminal) {
                log.warn("Application目标执行失败，applicationCode={}，versionId={}，executionId={}，durationMs={}",
                        snapshot.applicationCode(), snapshot.versionId(), executionId,
                        Duration.between(startedAt, Instant.now()).toMillis(), exception);
            }
            throw exception;
        }
    }

    /** 显式Skill只允许绑定到本次直接执行的已锁定领域专家，禁止跨Workflow或Supervisor猜测目标。 */
    private static void validateExplicitSkillTarget(ApplicationChatExecutionRequest request,
                                                    ChatDispatch.Capability dispatch) {
        List<String> explicitCodes = request.getExplicitSkillCodes();
        if (explicitCodes.isEmpty()) {
            return;
        }
        AgentRuntimeSnapshot agent = dispatch.hints().agent();
        if (dispatch.capability().getType() != ApplicationCapabilityType.AGENT || agent == null
                || agent.definition().getRole() != AgentRole.SPECIALIST) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "显式Skill只能用于直接执行的领域专家能力");
        }
        if (new HashSet<>(explicitCodes).size() != explicitCodes.size()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "显式Skill不能重复选择");
        }
        Map<String, com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot> bindings =
                agent.definition().resolveSkills().stream().collect(Collectors.toUnmodifiableMap(
                        binding -> binding.getSkill().getCode(), binding -> binding));
        for (String code : explicitCodes) {
            com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot binding = bindings.get(code);
            if (binding == null || binding.getSkill().getActivationMode() != SkillActivationMode.EXPLICIT_ONLY) {
                throw new BusinessException(HttpStatusEnum.CONFLICT,
                        "显式Skill不在目标AgentVersion的EXPLICIT_ONLY目录中：" + code);
            }
        }
    }

    private static boolean hasInterruptedCause(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ChatExecutionContext createExecutionContext(ApplicationChatExecutionRequest request,
                                                               ConversationContext conversationContext,
                                                               String executionId, String executionQuery) {
        AppRuntimeSnapshot snapshot = request.getApplication();
        // 每种执行路径都从已准备变量派生独立副本，避免执行期修改污染ConversationContext
        RuntimeVariables runtimeVariables = conversationContext.getRuntimeVariables().forkForQuery(executionQuery);
        return ChatExecutionContext.builder()
                .executionId(executionId)
                .runtimeVariables(runtimeVariables)
                .turn(request.getTurn())
                .eventPublisher(request.getEventPublisher())
                .memoryConfig(snapshot.definition().memoryConf())
                .conversationContext(conversationContext)
                .runtimePolicy(snapshot.definition().runtimePolicy())
                .contextBudget(snapshot.definition().contextPolicy().budget())
                .explicitSkillCodes(request.getExplicitSkillCodes())
                .tokenUsageAccumulator(initialUsage(conversationContext.getPreparationTokenUsage()))
                .modelCallCounter(new AtomicInteger(conversationContext.getPreparationModelCalls()))
                .toolCallCounter(new AtomicInteger(initialToolCalls(request)))
                .delegationCounter(new AtomicInteger())
                .resumeContext(request.getResumeContext())
                .build();
    }

    /**
     * 意图路由解析。
     * 平台交互恢复中或 Application 未启用意图路由时返回 SKIPPED。
     */
    private IntentRoutingResult routeIntent(ApplicationChatExecutionRequest request, ConversationContext context, boolean resumed) {
        if (resumed) {
            return IntentRoutingResult.skipped(context.routingQuery());
        }
        return intentRoutingService.route(context, request.getClarificationResponse());
    }

    /**
     * 确定执行用 query。路由结果的 executionQuery 优先（可包含槽位补充），
     * 否则使用上下文中的重写问题。
     */
    private static String resolveExecutionQuery(ConversationContext context, IntentRoutingResult routing) {
        if (Objects.isNull(routing.getExecutionQuery()) || routing.getExecutionQuery().isBlank()) {
            return context.routingQuery();
        }
        return routing.getExecutionQuery();
    }

    /**
     * 日志中可读的最终执行目标类型。
     */
    private static String targetType(ChatDispatch.Executable dispatch) {
        ChatDispatch.Capability capability = (ChatDispatch.Capability) dispatch;
        return capability.hints().agent() == null
                ? capability.capability().getType().name()
                : capability.hints().agent().definition().getRole().name();
    }

    /**
     * 监控读模型使用的稳定执行模式，避免前端根据能力类型反向推断。
     */
    private static String executionMode(ChatDispatch.Executable dispatch) {
        ChatDispatch.Capability capability = (ChatDispatch.Capability) dispatch;
        return switch (capability.capability().getType()) {
            case AGENT -> capability.hints().agent().definition().getRole()
                    == AgentRole.SUPERVISOR
                    ? "PLAN_EXECUTE"
                    : "REACT";
            case WORKFLOW -> "WORKFLOW";
            case TOOL -> throw new BusinessException(HttpStatusEnum.CONFLICT, "Application不支持直接调度Tool能力");
        };
    }

    /**
     * 意图路由未执行目标时，解析 ApplicationVersion 唯一锁定的 fallback Agent 能力。
     */
    private FallbackTarget resolveFallback(AppRuntimeSnapshot snapshot) {
        ApplicationCapabilityDefinition capability = resolveCapability(
                snapshot, snapshot.definition().fallback().cap(), ApplicationCapabilityType.AGENT);
        AgentRuntimeSnapshot agent = agentRuntimeService.resolveVersion(
                capability.getTargetId(), capability.getTargetVersionId());
        return new FallbackTarget(capability, agent);
    }

    /**
     * 校验意图路由映射与能力目录的一致性，返回已锁定版本的能力。
     */
    private static ApplicationCapabilityDefinition resolveIntentRoutingCapability(AppRuntimeSnapshot snapshot, ApplicationIntentMapping mapping, ApplicationCapabilityType expectedType) {
        IntentExecutionTargetType expectedTargetType = switch (expectedType) {
            case AGENT -> IntentExecutionTargetType.AGENT;
            case WORKFLOW -> IntentExecutionTargetType.WORKFLOW;
            case TOOL -> throw new BusinessException(HttpStatusEnum.CONFLICT, "意图路由不支持直接执行Tool能力");
        };
        if (Objects.isNull(mapping) || mapping.getTargetType() != expectedTargetType
                || Objects.isNull(mapping.getCapabilityCode()) || mapping.getCapabilityCode().isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application正式版本中的意图路由映射不完整");
        }
        ApplicationCapabilityDefinition capability = resolveCapability(snapshot, mapping.getCapabilityCode(), expectedType);
        // 交叉校验：路由映射中的 targetId/versionId 必须与能力目录一致
        if (!Objects.equals(mapping.getTargetId(), capability.getTargetId())
                || !Objects.equals(mapping.getTargetVersionId(), capability.getTargetVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图路由映射与能力目录锁定目标不一致");
        }
        return capability;
    }

    /**
     * 统一能力执行入口。内部由 CapabilityExecutionService 按类型分发到 AgentChat 或 WorkflowExecutor。
     */
    private ApplicationChatResult executeCapability(ChatDispatch.Capability dispatch,
                                                    ChatExecutionContext context) {
        CapabilityExecutionResult result = capabilityExecutionService.execute(
                CapabilityExecutionRequest.builder()
                        .capability(dispatch.capability())
                        .inputs(JSONObject.of("query", dispatch.query()))
                        .context(context)
                        .agent(dispatch.hints().agent())
                        .routingEvidence(dispatch.hints().routingEvidence())
                        .confirmedCapabilityCodes(dispatch.hints().confirmedCapabilityCodes())
                        .build());
        if (result != null && result.isInterrupted()) {
            return ApplicationChatResult.interrupted(context.tokenUsage(), result.interruption());
        }
        if (Objects.isNull(result) || Objects.isNull(result.content()) || result.content().isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "能力必须返回非空结果");
        }
        return ApplicationChatResult.completed(
                result.content(), context.tokenUsage(), result.traces(), result.modelInstanceId());
    }

    /**
     * 上下文准备已经越过硬边界时保留真实用量，并停止继续产生模型或Tool成本。
     */
    private static ApplicationChatResult budgetBoundaryResult(ChatExecutionContext context) {
        return ApplicationChatResult.completed(BUDGET_BOUNDARY_RESPONSE, context.tokenUsage(), List.of(), null);
    }

    /**
     * 从 Application 发布快照的能力目录中解析指定编码的能力。
     * 校验唯一性和类型一致性。
     */
    private static ApplicationCapabilityDefinition resolveCapability(AppRuntimeSnapshot snapshot, String capabilityCode,
                                                                     ApplicationCapabilityType expectedType) {
        List<ApplicationCapabilityDefinition> matches = snapshot.definition().capabilityCatalog().capabilities().stream()
                .filter(capability -> Objects.equals(capabilityCode, capability.getCode()))
                .toList();
        if (matches.size() != 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application正式版本中的能力编码不存在或不唯一：" + capabilityCode);
        }
        ApplicationCapabilityDefinition capability = matches.getFirst();
        if (capability.getType() != expectedType) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application正式版本中的能力类型与执行动作不一致");
        }
        return capability;
    }

    /**
     * Token 用量累加器初始化，计入上下文准备阶段的用量。
     */
    private static TokenUsageAccumulator initialUsage(TokenUsage usage) {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        accumulator.initialize(usage);
        return accumulator;
    }

    /**
     * Application Chat 的最终调度决策。
     * 路由结果在此处收敛为无需执行和需要执行两类稳定状态。
     */
    private sealed interface ChatDispatch permits ChatDispatch.Fixed, ChatDispatch.Clarify, ChatDispatch.Executable {

        /**
         * 直接返回固定回复。
         *
         * @param response 回复文本
         */
        record Fixed(String response) implements ChatDispatch {
        }

        /**
         * 发布澄清事件并返回提示文本。
         *
         * @param response      提示文本
         * @param clarification 结构化澄清事件
         */
        record Clarify(String response, ChatClarificationEventBO clarification) implements ChatDispatch {
        }

        /**
         * 需要创建并注册运行时执行上下文的调度决策。
         */
        sealed interface Executable extends ChatDispatch permits Capability {

            String query();
        }

        /**
         * 执行已锁定版本的Agent或Workflow能力。
         *
         * @param query      实际执行问题
         * @param capability 已解析并校验的能力
         */
        record Capability(String query, ApplicationCapabilityDefinition capability,
                          CapabilityDispatchHints hints) implements Executable {
        }
    }

    /**
     * 能力分发所需的运行时解析结果。
     *
     * @param agent                   能力目录锁定的 Agent 运行快照
     * @param routingEvidence         意图路由的命中证据
     * @param confirmedCapabilityCodes 已与用户确认的可执行能力编码集合
     */
    private record CapabilityDispatchHints(
            AgentRuntimeSnapshot agent,
            IntentRoutingEvidence routingEvidence,
            Set<String> confirmedCapabilityCodes) {

        private CapabilityDispatchHints {
            confirmedCapabilityCodes = confirmedCapabilityCodes == null
                    ? Set.of()
                    : Set.copyOf(confirmedCapabilityCodes);
        }
    }

    /**
     * 校验执行请求完整性。
     */
    private static void requireRequest(ApplicationChatExecutionRequest request) {
        Supplier<BusinessException> invalidRequest =
                () -> new BusinessException(HttpStatusEnum.BAD_REQUEST, "Application Chat执行请求不完整");
        Assert.notNull(request, invalidRequest);
        Object[] requiredFields = {
                request.getApplication(), request.getQuery(), request.getLoginUser(),
                request.getTurn(), request.getEventPublisher()
        };
        Assert.noNullElements(requiredFields, invalidRequest);
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
