package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentCollaborationScope;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerContextResolution;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.ApplicationChatExecutionService;
import com.lingxi.workbench.application.runtime.service.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.application.runtime.service.IntentRoutingService;
import com.lingxi.workbench.application.runtime.service.PlannerRuntimeCoordinator;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Application Chat 主执行流程。
 *
 * <h3>执行顺序（8 步）</h3>
 * <ol>
 *   <li>准备不可变 ConversationContext（窗口记忆、摘要、长期记忆、运行时变量）</li>
 *   <li>Planner 交互会话解析（新建或恢复中的澄清/确认）</li>
 *   <li>可选快速路由（Planner 恢复时跳过）→ 向量召回 + 意图匹配</li>
 *   <li>FIXED_RESPONSE / CLARIFICATION → 不调模型，直接返回</li>
 *   <li>Planner 降级：无快速路由命中且 Planner 启用时，进入 Planner 编排</li>
 *   <li>默认降级：无 Planner 时，使用 Application 配置的默认 ReAct Agent</li>
 *   <li>全部 AGENT/WORKFLOW 目标统一走 CapabilityExecutionService.executeCapability()</li>
 *   <li>finally 清理进程内执行上下文注册表</li>
 * </ol>
 *
 * <h3>关键设计</h3>
 * <ul>
 *   <li><b>能力目录</b>：ApplicationDefinition.capabilityCatalog 统一管理所有可执行能力，
 *       快速路由和默认入口都从同一目录解析，避免分散的 agentId/versionId 引用不一致</li>
 *   <li><b>CapabilityExecutionService</b>：AGENT 和 WORKFLOW 两种能力类型统一执行入口，
 *       内部根据类型分发到 AgentChat 或 WorkflowExecutor</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationChatExecutionServiceImpl implements ApplicationChatExecutionService {

    /**
     * 默认入口在能力目录中保留的唯一编码。区分快速路由中命中的具体能力。
     */
    private static final String DEFAULT_AGENT_CAPABILITY_CODE = "default_agent";

    /**
     * 统一能力执行入口，内部按 REACT_AGENT / WORKFLOW 类型分发。
     */
    private final CapabilityExecutionService capabilityExecutionService;
    /**
     * 进程内执行上下文注册表。Workflow 节点只携带 executionId，通过注册表读取上下文。
     */
    private final ChatExecutionContextRegistry executionContextRegistry;
    /**
     * 会话上下文准备：窗口记忆 + 摘要 + 长期记忆 + 运行时变量。
     */
    private final ConversationContextService conversationContextService;
    /**
     * 快速路由：向量语义匹配 + 确定性分差门控。
     */
    private final IntentRoutingService intentRoutingService;
    /**
     * Planner 编排协调器：plan → executeSteps → synthesize。
     */
    private final PlannerRuntimeCoordinator plannerRuntimeCoordinator;

    /**
     * 先准备共享上下文，再尝试可选快速路由，其他请求由Planner或默认ReAct Agent处理。
     *
     * <p>默认入口和快速路由统一通过发布快照中的能力目录执行，共享同一份可信对话上下文，
     * 并在 finally 中清理进程内状态。</p>
     */
    @Override
    public ApplicationChatResult execute(ApplicationChatExecutionRequest request) {
        // ① 校验服务端组装的可信请求，避免不完整数据进入上下文准备和路由阶段
        requireRequest(request);
        Instant startedAt = Instant.now();
        AppRuntimeSnapshot snapshot = request.getApplication();

        // ② 一次性准备窗口记忆、摘要、长期记忆和运行时变量，后续路由与执行共享同一上下文
        ConversationContext preparedContext = conversationContextService.prepare(
                snapshot, request.getQuery(), request.getLoginUser(), request.getTurn());

        // ③ 恢复上一轮Planner澄清或确认结果，并生成本轮真正参与路由的问题
        PlannerContextResolution plannerResolution = plannerRuntimeCoordinator.resolveInteraction(
                preparedContext, request.getClarificationResponse());

        ConversationContext context = plannerResolution.context();

        // ④ Planner恢复中跳过快速路由；其他请求按Application配置执行可选快速路由
        IntentRoutingResult routing = resolveFastRoute(request, context, plannerResolution);

        // ⑤ 合并快速路由结果、Planner策略和默认Agent，形成唯一且完整的最终调度决策
        ChatDispatch dispatch = this.dispatch(snapshot, context, plannerResolution, routing);

        // ⑥ 固定回复和澄清直接结束；Planner与Capability进入统一的运行上下文生命周期
        return switch (dispatch) {
            case ChatDispatch.Fixed fixed -> directResult(fixed.response(), context);
            case ChatDispatch.Clarify clarify -> clarificationResult(request, clarify, context);
            case ChatDispatch.Executable executable -> executeExecutable(request, context, executable, startedAt);
        };
    }

    /**
     * 将路由结果和Application策略合并为唯一、完整的最终调度决策。
     */
    private ChatDispatch dispatch(AppRuntimeSnapshot snapshot, ConversationContext context, PlannerContextResolution plannerResolution, IntentRoutingResult routing) {
        String executionQuery = resolveExecutionQuery(context, routing);
        return switch (routing.getAction()) {
            case FIXED_RESPONSE -> new ChatDispatch.Fixed(routing.getResponse());
            case CLARIFICATION -> new ChatDispatch.Clarify(routing.getResponse(), routing.getClarification());
            case EXECUTE_AGENT -> new ChatDispatch.Capability(executionQuery,
                    resolveFastRouteCapability(snapshot, routing.getMapping(), ApplicationCapabilityType.REACT_AGENT));
            case EXECUTE_WORKFLOW -> new ChatDispatch.Capability(executionQuery,
                    resolveFastRouteCapability(snapshot, routing.getMapping(), ApplicationCapabilityType.WORKFLOW));
            case SKIPPED, NO_MATCH -> resolveFallbackDispatch(snapshot, executionQuery, plannerResolution);
        };
    }

    /**
     * 快速路由未执行能力时，按发布策略选择Planner或默认ReAct Agent。
     */
    private ChatDispatch.Executable resolveFallbackDispatch(AppRuntimeSnapshot snapshot, String query, PlannerContextResolution plannerResolution) {
        if (snapshot.definition().plannerPolicy().isEnabled()) {
            return new ChatDispatch.Planner(query, plannerResolution.confirmedCapabilityCodes());
        }
        return new ChatDispatch.Capability(query, resolveDefaultCapability(snapshot));
    }

    private static ApplicationChatResult directResult(String response, ConversationContext context) {
        return new ApplicationChatResult(response, context.getPreparationTokenUsage(), List.of());
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

        // ① Planner先创建可持久化的runId；直接能力调用使用独立executionId
        String executionId = switch (dispatch) {
            case ChatDispatch.Planner ignored -> plannerRuntimeCoordinator.startRun(conversationContext);
            case ChatDispatch.Capability ignored -> IdUtil.getSnowflakeNextIdStr();
        };

        // ② 基于最终执行问题派生运行时变量，并构建本轮唯一的可写执行上下文
        ChatExecutionContext executionContext = createExecutionContext(request, conversationContext, executionId, dispatch.query());

        // ③ Workflow节点只携带executionId，通过注册表读取同一份可信上下文
        executionContextRegistry.register(executionContext);

        try {
            // ④ sealed分支保证这里只存在Planner编排和已锁定Capability执行两种合法状态
            ApplicationChatResult result = switch (dispatch) {
                case ChatDispatch.Planner planner -> executePlanner(PlannerRuntimeRequest.builder()
                        .runId(executionId)
                        .conversationContext(conversationContext)
                        .executionContext(executionContext)
                        .confirmedCapabilityCodes(planner.confirmedCapabilityCodes())
                        .startedAt(startedAt)
                        .build());
                case ChatDispatch.Capability capability -> executeCapability(
                        capability.capability(), capability.query(), executionContext);
            };

            // ⑤ 统一记录最终执行类型、轨迹数量和完整链路耗时
            log.info("Application目标执行完成，applicationCode={}，versionId={}，executionId={}，targetType={}，traceCount={}，durationMs={}",
                    snapshot.applicationCode(), snapshot.versionId(), executionId, targetType(dispatch),
                    result.traces().size(), Duration.between(startedAt, Instant.now()).toMillis());
            return result;
        } catch (RuntimeException exception) {
            if (!hasInterruptedCause(exception)) {
                log.warn("Application目标执行失败，applicationCode={}，versionId={}，executionId={}，durationMs={}",
                        snapshot.applicationCode(), snapshot.versionId(), executionId,
                        Duration.between(startedAt, Instant.now()).toMillis(), exception);
            }
            throw exception;
        } finally {
            // ⑥ 成功和失败都必须清理，避免用户会话数据滞留在进程内存中
            executionContextRegistry.remove(executionId);
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

    private static ChatExecutionContext createExecutionContext(ApplicationChatExecutionRequest request, ConversationContext conversationContext, String executionId, String executionQuery) {
        AppRuntimeSnapshot snapshot = request.getApplication();
        // 每种执行路径都从已准备变量派生独立副本，避免执行期修改污染ConversationContext
        RuntimeVariables runtimeVariables = conversationContext.getRuntimeVariables().forkForQuery(executionQuery);
        // 多Agent协作未启用时不创建协作作用域，普通ReAct与Workflow保持最小运行状态
        AgentCollaborationScope collaborationScope = Optional.ofNullable(snapshot.definition().collaboration())
                .filter(AgentCollaborationDefinition::enabled)
                .map(AgentCollaborationScope::root)
                .orElse(null);
        return ChatExecutionContext.builder()
                .executionId(executionId)
                .runtimeVariables(runtimeVariables)
                .turn(request.getTurn())
                .eventPublisher(request.getEventPublisher())
                .collaborationScope(collaborationScope)
                .memoryConfig(snapshot.definition().memoryConf())
                .conversationContext(conversationContext)
                .tokenUsageAccumulator(initialUsage(conversationContext.getPreparationTokenUsage()))
                .build();
    }

    /**
     * 快速路由解析。
     * Planner 恢复中或 Application 未启用快速路由时返回 SKIPPED。
     */
    private IntentRoutingResult resolveFastRoute(ApplicationChatExecutionRequest request,
                                                 ConversationContext context,
                                                 PlannerContextResolution plannerResolution) {
        if (plannerResolution.resumed() || !isFastRouteEnabled(request.getApplication())) {
            return IntentRoutingResult.skipped(context.routingQuery());
        }
        return intentRoutingService.route(context, request.getClarificationResponse());
    }

    /**
     * Application 是否启用了快速路由。
     */
    private static boolean isFastRouteEnabled(AppRuntimeSnapshot snapshot) {
        return Objects.nonNull(snapshot.definition().optionalFastRoutes())
                && snapshot.definition().optionalFastRoutes().isEnabled();
    }

    /**
     * 确定执行用 query。路由结果的 executionQuery 优先（可包含槽位补充），
     * 否则使用上下文中的上下文化 query。
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
        return switch (dispatch) {
            case ChatDispatch.Planner ignored -> "PLANNER";
            case ChatDispatch.Capability capability -> capability.capability().getType().name();
        };
    }

    /**
     * Planner 编排执行。
     */
    private ApplicationChatResult executePlanner(PlannerRuntimeRequest request) {
        PlanExecutionResult result = plannerRuntimeCoordinator.execute(request);
        return new ApplicationChatResult(result.content(), request.getExecutionContext().tokenUsage(), result.traces(), result.modelInstanceId());
    }

    /**
     * 无快速路由命中时，执行 Application 发布快照锁定的默认 ReAct Agent。
     * 必须校验 defaultAgent 与能力目录中 default_agent 的锁定目标一致。
     */
    private static ApplicationCapabilityDefinition resolveDefaultCapability(AppRuntimeSnapshot snapshot) {
        ApplicationCapabilityDefinition capability = resolveCapability(
                snapshot, DEFAULT_AGENT_CAPABILITY_CODE, ApplicationCapabilityType.REACT_AGENT);
        if (Objects.isNull(snapshot.definition().defaultAgent())
                || !Objects.equals(snapshot.definition().defaultAgent().agentId(), capability.getTargetId())
                || !Objects.equals(snapshot.definition().defaultAgent().agentVersionId(), capability.getTargetVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "默认Agent与保留能力锁定目标不一致");
        }
        return capability;
    }

    /**
     * 校验快速路由映射与能力目录的一致性，返回已锁定版本的能力。
     */
    private static ApplicationCapabilityDefinition resolveFastRouteCapability(AppRuntimeSnapshot snapshot, ApplicationIntentMapping mapping, ApplicationCapabilityType expectedType) {
        IntentExecutionTargetType expectedTargetType = switch (expectedType) {
            case REACT_AGENT -> IntentExecutionTargetType.AGENT;
            case WORKFLOW -> IntentExecutionTargetType.WORKFLOW;
            case TOOL -> throw new BusinessException(HttpStatusEnum.CONFLICT, "快速路由不支持直接执行Tool能力");
        };
        if (Objects.isNull(mapping) || mapping.getTargetType() != expectedTargetType
                || Objects.isNull(mapping.getCapabilityCode()) || mapping.getCapabilityCode().isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application正式版本中的快速路由映射不完整");
        }
        ApplicationCapabilityDefinition capability = resolveCapability(snapshot, mapping.getCapabilityCode(), expectedType);
        // 交叉校验：路由映射中的 targetId/versionId 必须与能力目录一致
        if (!Objects.equals(mapping.getTargetId(), capability.getTargetId())
                || !Objects.equals(mapping.getTargetVersionId(), capability.getTargetVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "快速路由映射与能力目录锁定目标不一致");
        }
        return capability;
    }

    /**
     * 统一能力执行入口。内部由 CapabilityExecutionService 按类型分发到 AgentChat 或 WorkflowExecutor。
     */
    private ApplicationChatResult executeCapability(ApplicationCapabilityDefinition capability, String query,
                                                    ChatExecutionContext context) {
        CapabilityExecutionResult result = capabilityExecutionService.execute(
                new CapabilityExecutionRequest(capability, JSONObject.of("query", query), context));
        if (Objects.isNull(result) || Objects.isNull(result.content()) || result.content().isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "能力必须返回非空结果");
        }
        return new ApplicationChatResult(result.content(), context.tokenUsage(), result.traces(), result.modelInstanceId());
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
        sealed interface Executable extends ChatDispatch permits Planner, Capability {

            String query();
        }

        /**
         * 进入Planner编排。
         *
         * @param query                    实际执行问题
         * @param confirmedCapabilityCodes 用户已经确认的能力编码
         */
        record Planner(String query, Set<String> confirmedCapabilityCodes) implements Executable {

            public Planner {
                confirmedCapabilityCodes = Set.copyOf(confirmedCapabilityCodes);
            }
        }

        /**
         * 执行已锁定版本的Agent或Workflow能力。
         *
         * @param query      实际执行问题
         * @param capability 已解析并校验的能力
         */
        record Capability(String query, ApplicationCapabilityDefinition capability) implements Executable {
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

}
