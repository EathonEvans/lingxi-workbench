package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.application.runtime.service.agent.AgentExecutorRegistry;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeWorkflowVersionEntity;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.agent.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.capability.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * 能力目录统一执行器：锁定能力的目标解析、完整性校验和执行实现。
 * <p>按能力类型（AGENT/WORKFLOW/TOOL）分发执行：AGENT 走执行器注册表、
 * WORKFLOW 走工作流执行器、TOOL 走工具运行时；统一校验锁定版本与 checksum、
 * 控制执行超时与结果 Token 预算，并对外发布 Tool 执行生命周期事件。
 */
@Service
@RequiredArgsConstructor
public class CapabilityExecutionServiceImpl implements CapabilityExecutionService {

    private static final String TOOL_EXECUTION_FAILED_CODE = "TOOL_EXECUTION_FAILED";

    private final ObjectProvider<AgentExecutorRegistry> agentExecutorRegistryProvider;
    private final AgentRuntimeService agentRuntimeService;
    private final WorkflowRuntimeRepo workflowRuntimeRepo;
    private final WorkflowExecutor workflowExecutor;
    private final ToolRuntimeRepo toolRuntimeRepo;
    private final ToolRuntimeService toolRuntimeService;
    private final ApplicationCapabilityChecksum checksum;
    private final TokenCountEstimator tokenCountEstimator;
    private final ObservationRegistry observationRegistry;

    /**
     * 执行一个能力请求，能力目录的统一入口。
     * <p>先校验请求完整性，再以 Micrometer Observation 观测包裹执行过程，
     * 观测维度包含能力类型与能力编码，便于链路追踪与指标聚合。
     *
     * @param request 能力执行请求（能力定义、执行上下文与输入）
     * @return 能力执行结果（回答内容、模型实例ID与追踪信息）
     */
    @Override
    public CapabilityExecutionResult execute(CapabilityExecutionRequest request) {
        requireRequest(request);
        ApplicationCapabilityDefinition capability = request.getCapability();
        Observation observation = Observation.createNotStarted("lingxi.ai.capability", observationRegistry)
                .contextualName("capability-" + capability.getType().name().toLowerCase())
                .lowCardinalityKeyValue("lingxi.capability.type", capability.getType().name())
                .highCardinalityKeyValue("lingxi.capability.code", capability.getCode());
        return observation.observe(() -> executeCapability(request));
    }

    /**
     * 按能力类型分发执行，并统一做超时与结果 Token 预算控制。
     * <p>AGENT/WORKFLOW 由 executeWithinTimeout 包裹超时执行，TOOL 在 executeTool
     * 内部自行控制超时并发布生命周期事件；执行结果须满足发布快照的 Token 预算。
     *
     * @param request 能力执行请求
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeCapability(CapabilityExecutionRequest request) {
        // 取上下文剩余时间与能力配置超时中的较小值，避免突破整体会话预算
        Duration timeout = request.getContext().remainingTimeout(Duration.ofSeconds(request.getCapability().getTimeoutSeconds()));

        // 按能力类型分发：AGENT/WORKFLOW 包一层超时控制，TOOL 自带超时与事件发布
        CapabilityExecutionResult result = switch (request.getCapability().getType()) {
            // Agent内部按Application动态硬截止处理活跃流式宽限，外层固定timeout会提前取消仍在输出的回答。
            case AGENT -> executeAgent(request);
            case WORKFLOW -> executeWithinTimeout(request, timeout, () -> executeWorkflow(request));
            case TOOL -> executeTool(request, timeout);
        };
        // 结果 Token 预算校验：超出 Application 发布快照预算即拒绝该结果
        if (tokenCountEstimator.estimate(result.content()) > request.getCapability().getResultTokenBudget()) {
            throw invalid("能力结果超过Application发布快照Token预算");
        }
        return result;
    }

    /**
     * 在独立线程上执行能力并施加超时限制。
     * <p>切换到 boundedElastic 调度器执行以避免阻塞调用线程，超时异常统一映射为
     * “能力执行超时”业务异常；执行时透传当前观测，保持链路追踪上下文。
     *
     * @param request   能力执行请求（超时信息取自能力定义）
     * @param timeout   允许的最大执行时长
     * @param execution 实际执行逻辑
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeWithinTimeout(CapabilityExecutionRequest request, Duration timeout,
                                                           Callable<CapabilityExecutionResult> execution) {
        Observation parent = observationRegistry.getCurrentObservation();
        return Mono.fromCallable(() -> callWithParent(execution, parent))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(timeout)
                .onErrorMap(TimeoutException.class, exception -> invalid("能力执行超时：" + request.getCapability().getCode()))
                .block();
    }

    /**
     * 在父观测作用域内执行能力调用，保持观测链路的父子关系。
     * <p>父观测为空时直接执行；否则打开父观测作用域再执行，
     * 使线程切换后的观测依然挂载在正确的链路上。
     *
     * @param execution 实际执行逻辑
     * @param parent    父观测，可能为空
     * @return 能力执行结果
     * @throws Exception 执行逻辑抛出的原始异常
     */
    private static CapabilityExecutionResult callWithParent(Callable<CapabilityExecutionResult> execution, Observation parent) throws Exception {
        if (Objects.isNull(parent)) {
            return execution.call();
        }
        try (Observation.Scope ignored = parent.openScope()) {
            return execution.call();
        }
    }

    /**
     * 执行 AGENT 类型能力。
     * <p>前置校验锁定版本存在、预解析 Agent 与能力锁定目标一致、checksum 与发布快照一致；
     * 主Agent仅允许作为 Application fallback 入口执行，领域专家可由快速路由或计划步骤调用；
     * 最终按 Agent 角色从执行器注册表取对应执行器执行。
     *
     * @param request 能力执行请求
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeAgent(CapabilityExecutionRequest request) {
        ApplicationCapabilityDefinition capability = request.getCapability();
        requireLockedVersion(capability);
        // 未预解析时按锁定目标解析 Agent 版本快照
        AgentRuntimeSnapshot agent = request.getAgent() == null
                ? agentRuntimeService.resolveVersion(capability.getTargetId(), capability.getTargetVersionId())
                : request.getAgent();
        // 预解析结果必须与能力锁定目标一致，防止版本漂移
        if (!Objects.equals(agent.agentId(), capability.getTargetId())
                || !Objects.equals(agent.agentVersionId(), capability.getTargetVersionId())) {
            throw invalid("预解析AgentVersion与能力锁定目标不一致");
        }
        // 主Agent只能作为Application fallback入口，防止计划把另一个主Agent作为业务步骤递归执行
        if (agent.definition().getRole() == AgentRole.SUPERVISOR) {
            ConversationContext conversationContext = request.getContext().getConversationContext();
            if (conversationContext == null || conversationContext.getApplicationSnapshot().definition() == null || !Objects.equals(
                    capability.getCode(),
                    conversationContext.getApplicationSnapshot().definition().fallback().cap())) {
                throw invalid("主Agent只能作为Application fallback入口执行");
            }
        }
        // checksum 校验：锁定版本配置必须与 Application 发布快照一致
        requireChecksum(capability, checksum.calculate(capability.getTargetVersionId()));
        // 按 Agent 角色从执行器注册表取对应执行器并分发执行
        return agentExecutorRegistryProvider.getObject()
                .require(agent.definition().getRole())
                .execute(AgentExecutionRequest.builder()
                        .capability(capability)
                        .agent(agent)
                        .query(requireQuery(request.getInputs()))
                        .executionContext(request.getContext())
                        .routingEvidence(request.getRoutingEvidence())
                        .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                        .build());
    }

    /**
     * 执行 WORKFLOW 类型能力。
     * <p>按锁定目标加载 Workflow 版本快照并校验 checksum；将能力 query 输入包装为
     * 工作流请求执行；结果必须输出非空 response；并从首个 Agent 节点解析模型实例ID
     * 用于结果归因。
     *
     * @param request 能力执行请求
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeWorkflow(CapabilityExecutionRequest request) {
        ApplicationCapabilityDefinition capability = request.getCapability();
        requireLockedVersion(capability);
        // 按锁定目标加载 Workflow 版本快照，不存在视为发布快照失效
        RuntimeWorkflowVersionEntity version = workflowRuntimeRepo
                .findVersion(capability.getTargetId(), capability.getTargetVersionId())
                .orElseThrow(() -> invalid("Application锁定的WorkflowVersion不存在"));
        WorkflowDefinition definition = Objects.requireNonNull(version.getDefinitionSnapshot(), "WorkflowVersion定义不能为空");
        requireChecksum(capability, checksum.calculate(definition));
        WorkflowExecutionRequest workflowRequest = new WorkflowExecutionRequest(
                request.getContext().getExecutionId(), capability.getTargetId(), capability.getTargetVersionId(), Map.of("query", requireQuery(request.getInputs())));
        WorkflowExecutionResult result = workflowExecutor.execute(definition, workflowRequest);
        // 工作流必须输出非空 response 才能作为能力结果
        String answer = resolveAnswer(result).orElseThrow(() -> invalid("WorkflowVersion必须输出非空response"));
        // 取首个 Agent 节点的模型实例ID作为结果归因依据
        Long modelInstanceId = definition.nodes().stream()
                .filter(node -> node.config() instanceof AgentNodeConfig)
                .map(node -> (AgentNodeConfig) node.config())
                .findFirst()
                .map(config -> agentRuntimeService.resolveVersion(config.agentId(), config.agentVersionId()))
                .map(agent -> agent.definition().getChatModelInstanceId())
                .orElse(null);
        return new CapabilityExecutionResult(answer, modelInstanceId, result.traces());
    }

    /**
     * 执行 TOOL 类型能力。
     * <p>加载启用中的 Tool 定义并校验 checksum；预留一次工具调用配额后生成调用标识，
     * 通过事件发布器对外发布 RUNNING/SUCCEEDED/FAILED 生命周期事件；调用本身在独立
     * 线程上执行并受超时限制，结果必须非空。
     *
     * @param request 能力执行请求
     * @param timeout 允许的最大执行时长
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeTool(CapabilityExecutionRequest request, Duration timeout) {
        ApplicationCapabilityDefinition capability = request.getCapability();
        // 仅允许执行启用中的 Tool，锁定目标失效则拒绝
        RuntimeToolEntity toolDefinition = toolRuntimeRepo.findActiveById(capability.getTargetId())
                .orElseThrow(() -> invalid("Application锁定的Tool不存在或已停用"));
        requireChecksum(capability, checksum.calculate(toolConfiguration(toolDefinition)));
        AgentTool tool = toolRuntimeService.resolveActiveTool(capability.getTargetId());
        // 预留工具调用配额，防止会话内工具调用次数超限
        request.getContext().reserveToolCalls(1);
        String invocationId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        // 发布 RUNNING 事件，开始工具调用
        publishTool(request.getContext(), tool, new ToolEventState(invocationId, AgentToolExecutionStatus.RUNNING, null, null));
        try {
            // 独立线程执行工具回调并施加超时，超时统一映射为业务异常
            Observation parent = observationRegistry.getCurrentObservation();
            String result = Mono.fromCallable(() -> callToolWithParent(tool, request.getInputs(), parent))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(timeout)
                    .onErrorMap(TimeoutException.class, exception ->
                            invalid("能力执行超时：" + capability.getCode()))
                    .block();
            // 空结果视为无效，工具必须返回非空内容
            if (result == null || result.isBlank()) {
                throw invalid("Tool能力必须返回非空结果");
            }
            publishTool(request.getContext(), tool, new ToolEventState(
                    invocationId, AgentToolExecutionStatus.SUCCEEDED, elapsedMillis(startedAt), null));
            return new CapabilityExecutionResult(result, null, List.of());
        } catch (RuntimeException exception) {
            // 任何运行时异常都发布 FAILED 事件后向上抛出
            publishTool(request.getContext(), tool, new ToolEventState(
                    invocationId, AgentToolExecutionStatus.FAILED,
                    elapsedMillis(startedAt), TOOL_EXECUTION_FAILED_CODE));
            throw exception;
        }
    }

    /**
     * 在父观测作用域内执行工具回调。
     * <p>父观测为空时直接调用；否则打开父观测作用域，保证工具调用产生的
     * 观测事件挂载在正确的链路上。输入参数序列化为 JSON 字符串传入回调。
     *
     * @param tool   工具定义与回调
     * @param inputs 工具入参
     * @param parent 父观测，可能为空
     * @return 工具回调返回的字符串结果
     */
    private static String callToolWithParent(AgentTool tool, JSONObject inputs, Observation parent) {
        if (Objects.isNull(parent)) {
            return tool.callback().call(JSON.toJSONString(inputs));
        }
        try (Observation.Scope ignored = parent.openScope()) {
            return tool.callback().call(JSON.toJSONString(inputs));
        }
    }

    /**
     * 组装 Tool 的配置快照，用于与 Application 发布快照做 checksum 比对。
     * <p>仅取影响行为的配置字段（编码、来源类型、MCP 服务、远端工具名与参数Schema），
     * 任一字段变化都会导致 checksum 不一致。
     *
     * @param tool 工具实体
     * @return 配置快照 JSON
     */
    private static JSONObject toolConfiguration(RuntimeToolEntity tool) {
        JSONObject configuration = new JSONObject();
        configuration.put("toolCode", tool.getCode());
        configuration.put("sourceType", tool.getSourceType());
        configuration.put("mcpServerId", tool.getMcpServerId());
        configuration.put("remoteToolName", tool.getRemoteToolName());
        configuration.put("inputSchema", tool.getParameterSchema());
        return configuration;
    }

    /**
     * 向执行上下文的事件发布器发布工具执行状态事件。
     * <p>只透出安全的元信息（调用标识、工具编码、显示名、状态、耗时与错误码），
     * 不包含调用参数与原始结果。
     *
     * @param context 执行上下文
     * @param tool    工具定义
     * @param event   待发布的工具事件状态
     */
    private static void publishTool(ChatExecutionContext context, AgentTool tool, ToolEventState event) {
        context.getEventPublisher().publishTool(AgentToolExecutionEvent.builder()
                .invocationId(event.invocationId())
                .toolCode(tool.definition().getCode())
                .displayName(tool.definition().getDisplayName())
                .status(event.status())
                .durationMillis(event.durationMillis())
                .errorCode(event.errorCode())
                .build());
    }

    /**
     * 从能力输入中提取必填的 query 字段。
     * <p>Agent/Workflow 能力依赖文本 query 输入，缺失或空白直接拒绝，返回去除首尾空白后的值。
     *
     * @param inputs 能力输入
     * @return 非空的 query 文本
     */
    private static String requireQuery(JSONObject inputs) {
        String query = inputs.getString("query");
        if (query == null || query.isBlank()) {
            throw invalid("Agent或Workflow能力缺少query输入");
        }
        return query.trim();
    }

    /**
     * 从工作流输出中解析 response 字段作为能力回答。
     * <p>仅接受非空的 String 类型 response，否则返回空 Optional 由调用方拒绝。
     *
     * @param result 工作流执行结果
     * @return 非空回答，缺失时为空
     */
    private static Optional<String> resolveAnswer(WorkflowExecutionResult result) {
        Object response = result.outputs().get("response");
        return response instanceof String answer && !answer.isBlank() ? Optional.of(answer) : Optional.empty();
    }

    /**
     * 校验能力执行请求的完整性与运行预算。
     * <p>请求、能力定义、类型、锁定目标、输入与上下文缺一不可；
     * 超时秒数与结果 Token 预算必须为正数，防止无预算执行。
     *
     * @param request 能力执行请求
     */
    private static void requireRequest(CapabilityExecutionRequest request) {
        if (request == null || request.getCapability() == null || request.getCapability().getType() == null
                || request.getCapability().getTargetId() == null || request.getInputs() == null || request.getContext() == null) {
            throw invalid("能力执行请求不完整");
        }
        if (request.getCapability().getTimeoutSeconds() == null || request.getCapability().getTimeoutSeconds() < 1
                || request.getCapability().getResultTokenBudget() == null
                || request.getCapability().getResultTokenBudget() < 1) {
            throw invalid("能力运行预算不完整");
        }
    }

    /**
     * 校验能力已锁定目标版本。
     * <p>未锁定版本的能力无法解析运行时快照，直接拒绝执行。
     *
     * @param capability 应用能力定义
     */
    private static void requireLockedVersion(ApplicationCapabilityDefinition capability) {
        if (capability.getTargetVersionId() == null) {
            throw invalid("能力缺少锁定目标版本");
        }
    }

    /**
     * 校验能力当前配置与 Application 发布快照的 checksum 一致。
     * <p>运行期配置一旦与发布时不一致（被修改或漂移），视为快照失效并拒绝执行。
     *
     * @param capability     应用能力定义
     * @param actualChecksum 运行时计算出的实际 checksum
     */
    private static void requireChecksum(ApplicationCapabilityDefinition capability, String actualChecksum) {
        if (!Objects.equals(capability.getConfigurationChecksum(), actualChecksum)) {
            throw invalid("能力配置与Application发布快照不一致：" + capability.getCode());
        }
    }

    /**
     * 将纳秒起始时刻换算为经过的毫秒数。
     *
     * @param startedAt 起始时刻（纳秒）
     * @return 经过的毫秒数
     */
    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * 构造能力执行失败的统一业务异常（409 冲突语义）。
     * <p>能力校验类失败统一使用该异常，便于上层统一捕获与错误归类。
     *
     * @param message 失败原因描述
     * @return 业务异常
     */
    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }

    /**
     * 对外发布的安全Tool状态，不包含调用参数和原始结果。
     *
     * @param invocationId   调用标识
     * @param status         执行状态
     * @param durationMillis 执行耗时
     * @param errorCode      失败编码
     */
    private record ToolEventState(String invocationId, AgentToolExecutionStatus status,
                                  Long durationMillis, String errorCode) {
    }
}
