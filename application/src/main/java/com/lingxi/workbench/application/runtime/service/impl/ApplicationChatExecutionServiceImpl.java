package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.model.AgentCollaborationScope;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerContextResolution;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ApplicationChatExecutionService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.IntentRoutingService;
import com.lingxi.workbench.application.runtime.service.PlannerRuntimeCoordinator;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationChatExecutionServiceImpl implements ApplicationChatExecutionService {

    private final WorkflowExecutor workflowExecutor;
    private final AgentChatService agentChatService;
    private final AgentRuntimeService agentRuntimeService;
    private final ChatExecutionContextRegistry executionContextRegistry;
    private final ConversationContextService conversationContextService;
    private final IntentRoutingService intentRoutingService;
    private final WorkflowRuntimeRepo workflowRuntimeRepo;
    private final PlannerRuntimeCoordinator plannerRuntimeCoordinator;

    /**
     * 先准备共享上下文，再尝试可选快速路由，其他请求由Planner或默认ReAct Agent处理。
     *
     * <p>只有 WORKFLOW 目标进入 WorkflowExecutor；AGENT 目标直接调用
     * {@link AgentChatService}。两种执行目标共享同一份可信对话上下文，并在 finally
     * 中统一清理，避免任意节点、模型或工具异常后遗留进程内状态。</p>
     */
    @Override
    public ApplicationChatResult execute(ApplicationRuntimeSnapshot snapshot, String query, LoginUser loginUser,
                                         ChatTurnBO turn, ClarificationResponseRequest clarificationResponse,
                                         ChatExecutionEventPublisher eventPublisher) {

        Instant startedAt = Instant.now();
        ConversationContext conversationContext = conversationContextService.prepare(snapshot, query, loginUser, turn);

        PlannerContextResolution plannerResolution = plannerRuntimeCoordinator.resolveInteraction(conversationContext, clarificationResponse);
        conversationContext = plannerResolution.context();
        boolean hasIntentRouting = hasIntentRouting(snapshot);
        IntentRoutingResult routing = hasIntentRouting && !plannerResolution.resumed()
                ? intentRoutingService.route(conversationContext, clarificationResponse)
                : null;

        // 固定回复直接响应内容
        if (Objects.nonNull(routing) && routing.getAction() == IntentRoutingAction.FIXED_RESPONSE) {
            return new ApplicationChatResult(routing.getResponse(), conversationContext.getPreparationTokenUsage(), List.of());
        }
        // 意图澄清发布意图澄清事件，等待用户输入
        if (Objects.nonNull(routing) && routing.getAction() == IntentRoutingAction.CLARIFICATION) {
            eventPublisher.publishClarification(routing.getClarification());
            return new ApplicationChatResult(routing.getResponse(), conversationContext.getPreparationTokenUsage(), List.of());
        }
        boolean routeFallsThrough = Objects.isNull(routing)
                || routing.getAction() == IntentRoutingAction.NO_MATCH;
        boolean usePlanner = snapshot.definition().plannerPolicy().isEnabled()
                && (plannerResolution.resumed() || routeFallsThrough);
        String executionId = usePlanner
                ? plannerRuntimeCoordinator.startRun(conversationContext)
                : IdUtil.getSnowflakeNextIdStr();
        String executionQuery = conversationContext.routingQuery();
        if (Objects.nonNull(routing)
                && routing.getExecutionQuery() != null
                && !routing.getExecutionQuery().isBlank()) {
            executionQuery = routing.getExecutionQuery();
        }
        // 路由结果可以补充槽位或上下文化问题，但用户原始输入始终保留在共享上下文中。
        RuntimeVariables runtimeVariables = conversationContext.getRuntimeVariables().forkForQuery(executionQuery);
        AgentCollaborationScope collaborationScope = Optional.ofNullable(snapshot.definition().collaboration())
                .filter(AgentCollaborationDefinition::enabled)
                .map(AgentCollaborationScope::root)
                .orElse(null);
        // 将变量容器和已落库的对话轮次封装为服务端可信上下文。
        ChatExecutionContext context = ChatExecutionContext.builder()
                .executionId(executionId)
                .runtimeVariables(runtimeVariables)
                .turn(turn)
                .eventPublisher(eventPublisher)
                .collaborationScope(collaborationScope)
                .memoryConfig(snapshot.definition().memory())
                .conversationContext(conversationContext)
                .tokenUsageAccumulator(initialUsage(conversationContext.getPreparationTokenUsage()))
                .build();
        // Workflow 节点仅携带 executionId，通过注册表读取同一份可信上下文。
        executionContextRegistry.register(context);
        try {
            ApplicationChatResult chatResult;
            if (usePlanner) {
                chatResult = executePlanner(executionId, conversationContext, context, plannerResolution, startedAt);
            } else if (routeFallsThrough) {
                chatResult = executeDefaultEntry(snapshot, executionId, executionQuery, context);
            } else {
                chatResult = executeSelectedRoute(snapshot, routing, executionId, executionQuery, context);
            }
            log.info(
                    "Application意图目标执行完成，applicationCode={}，"
                            + "applicationVersionId={}，executionId={}，targetType={}，"
                            + "traceCount={}，durationMs={}",
                    snapshot.applicationCode(),
                    snapshot.applicationVersionId(),
                    executionId,
                    targetType(snapshot, routing, usePlanner, routeFallsThrough),
                    chatResult.traces().size(),
                    Duration.between(startedAt, Instant.now()).toMillis());
            return chatResult;
        } catch (RuntimeException exception) {
            log.warn(
                    "Application意图目标执行失败，applicationCode={}，"
                            + "applicationVersionId={}，executionId={}，durationMs={}",
                    snapshot.applicationCode(),
                    snapshot.applicationVersionId(),
                    executionId,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    exception);
            throw exception;
        } finally {
            // 成功和失败都必须清理，避免用户会话数据滞留在进程内存中。
            executionContextRegistry.remove(executionId);
        }
    }

    private static boolean hasIntentRouting(ApplicationRuntimeSnapshot snapshot) {
        if (snapshot.definition().schemaVersion() >= 5) {
            return Objects.nonNull(snapshot.definition().optionalFastRoutes())
                    && snapshot.definition().optionalFastRoutes().isEnabled();
        }
        return Objects.nonNull(snapshot.definition().intentBinding())
                && snapshot.definition().intentBinding().isEnabled();
    }

    private ApplicationChatResult executeSelectedRoute(ApplicationRuntimeSnapshot snapshot,
                                                       IntentRoutingResult routing,
                                                       String executionId,
                                                       String executionQuery,
                                                       ChatExecutionContext context) {
        return switch (routing.getAction()) {
            case EXECUTE_AGENT -> executeAgent(routing.getMapping(), executionQuery, context);
            case EXECUTE_WORKFLOW -> executeWorkflow(
                    snapshot, routing.getMapping(), executionId, executionQuery, context);
            default -> throw new BusinessException(HttpStatusEnum.CONFLICT, "意图路由返回了不可执行动作");
        };
    }

    private static String targetType(ApplicationRuntimeSnapshot snapshot, IntentRoutingResult routing,
                                     boolean usePlanner, boolean routeFallsThrough) {
        if (usePlanner) {
            return "PLANNER";
        }
        if (routeFallsThrough) {
            return Objects.nonNull(snapshot.definition().defaultAgent()) ? "DEFAULT_AGENT" : "LEGACY_WORKFLOW";
        }
        return routing.getMapping().getTargetType().name();
    }

    private ApplicationChatResult executePlanner(String runId, ConversationContext conversationContext,
                                                 ChatExecutionContext context,
                                                 PlannerContextResolution resolution, Instant startedAt) {
        PlanExecutionResult result = plannerRuntimeCoordinator.execute(PlannerRuntimeRequest.builder()
                .runId(runId)
                .conversationContext(conversationContext)
                .executionContext(context)
                .confirmedCapabilityCodes(resolution.confirmedCapabilityCodes())
                .startedAt(startedAt)
                .build());
        return new ApplicationChatResult(
                result.content(), context.tokenUsage(), result.traces(), result.modelInstanceId());
    }

    /**
     * 无意图路由的新版本执行默认 ReAct Agent；历史版本继续执行内嵌 Workflow。
     */
    private ApplicationChatResult executeDefaultEntry(ApplicationRuntimeSnapshot snapshot, String executionId, String query,
                                                      ChatExecutionContext context) {
        ApplicationAgentReference defaultAgent = snapshot.definition().defaultAgent();
        if (Objects.nonNull(defaultAgent)) {
            Long modelInstanceId = resolveAgentModelInstanceId(defaultAgent.agentId(), defaultAgent.agentVersionId());
            String answer = agentChatService.chat(defaultAgent.agentId(), defaultAgent.agentVersionId(), query, context);
            if (answer == null || answer.isBlank()) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "默认AgentVersion必须返回非空回答");
            }
            return new ApplicationChatResult(answer, context.tokenUsage(), List.of(), modelInstanceId);
        }
        return executeWorkflow(snapshot, null, executionId, query, context);
    }

    /**
     * AGENT 叶子直接执行发布时锁定的 AgentVersion，不经过 Workflow 引擎。
     */
    private ApplicationChatResult executeAgent(ApplicationIntentMapping mapping,
                                               String query,
                                               ChatExecutionContext context) {
        if (Objects.isNull(mapping)
                || mapping.getTargetType() != IntentExecutionTargetType.AGENT
                || Objects.isNull(mapping.getTargetId())
                || Objects.isNull(mapping.getTargetVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application正式版本中的Agent映射不完整");
        }
        Long modelInstanceId = resolveAgentModelInstanceId(mapping.getTargetId(), mapping.getTargetVersionId());
        String answer = agentChatService.chat(mapping.getTargetId(), mapping.getTargetVersionId(), query, context);
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion必须返回非空回答");
        }
        return new ApplicationChatResult(answer, context.tokenUsage(), List.of(), modelInstanceId);
    }

    /**
     * WORKFLOW 叶子加载并执行发布时锁定的独立 WorkflowVersion。
     */
    private ApplicationChatResult executeWorkflow(
            ApplicationRuntimeSnapshot snapshot,
            ApplicationIntentMapping mapping,
            String executionId,
            String query,
            ChatExecutionContext context) {
        WorkflowDefinition workflow = resolveWorkflow(snapshot, mapping);
        Long executionVersionId = Objects.isNull(mapping)
                ? snapshot.applicationVersionId()
                : mapping.getTargetVersionId();
        WorkflowExecutionRequest request = new WorkflowExecutionRequest(
                executionId,
                String.valueOf(executionVersionId),
                Map.of("query", query));
        WorkflowExecutionResult result = workflowExecutor.execute(workflow, request);
        String answer = resolveAnswer(result.outputs()).orElseThrow(() ->
                new BusinessException(HttpStatusEnum.CONFLICT, "WorkflowVersion必须输出非空字符串response"));
        Long modelInstanceId = workflow.nodes().stream()
                .filter(node -> node.config() instanceof AgentNodeConfig)
                .map(node -> (AgentNodeConfig) node.config())
                .findFirst()
                .map(config -> resolveAgentModelInstanceId(config.agentId(), config.agentVersionId()))
                .orElse(null);
        return new ApplicationChatResult(answer, context.tokenUsage(), result.traces(), modelInstanceId);
    }

    /**
     * 根据 Application 发布映射加载锁定的独立 WorkflowVersion。
     */
    private WorkflowDefinition resolveWorkflow(
            ApplicationRuntimeSnapshot snapshot,
            ApplicationIntentMapping mapping) {
        if (Objects.isNull(mapping)) {
            return Optional.ofNullable(snapshot.definition().workflow())
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "历史Application缺少内嵌Workflow"));
        }
        if (mapping.getTargetType() != IntentExecutionTargetType.WORKFLOW
                || Objects.isNull(mapping.getTargetId())
                || Objects.isNull(mapping.getTargetVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application正式版本中的Workflow映射不完整");
        }
        return workflowRuntimeRepo.findVersion(mapping.getTargetId(), mapping.getTargetVersionId())
                .map(com.lingxi.workbench.application.runtime.model.entity.RuntimeWorkflowVersionEntity::getDefinitionSnapshot)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的WorkflowVersion不存在"));
    }

    private Long resolveAgentModelInstanceId(Long agentId, Long agentVersionId) {
        AgentRuntimeSnapshot agent = agentRuntimeService.resolveVersion(agentId, agentVersionId);
        return Optional.ofNullable(agent.definition().getChatModelInstanceId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少对话模型"));
    }

    private static TokenUsageAccumulator initialUsage(TokenUsage usage) {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        accumulator.initialize(usage);
        return accumulator;
    }

    /**
     * 优先读取约定的 response 输出；单节点工作流兼容唯一字符串输出。
     */
    private static Optional<String> resolveAnswer(Map<String, Object> outputs) {
        return Optional.ofNullable(outputs.get("response"))
                .or(() -> outputs.size() == 1 ? Optional.ofNullable(outputs.values().iterator().next()) : Optional.empty())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(answer -> !answer.isBlank());
    }
}
