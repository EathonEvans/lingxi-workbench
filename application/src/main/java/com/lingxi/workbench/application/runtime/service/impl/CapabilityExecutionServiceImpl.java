package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeWorkflowVersionEntity;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.ToolRuntimeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
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
 * 锁定能力的目标解析、完整性校验和执行实现。
 */
@Service
@RequiredArgsConstructor
public class CapabilityExecutionServiceImpl implements CapabilityExecutionService {

    private static final String TOOL_EXECUTION_FAILED_CODE = "TOOL_EXECUTION_FAILED";

    private final AgentChatService agentChatService;
    private final AgentRuntimeService agentRuntimeService;
    private final WorkflowRuntimeRepo workflowRuntimeRepo;
    private final WorkflowExecutor workflowExecutor;
    private final ToolRuntimeRepo toolRuntimeRepo;
    private final ToolRuntimeService toolRuntimeService;
    private final ApplicationCapabilityChecksum checksum;
    private final TokenCountEstimator tokenCountEstimator;

    @Override
    public CapabilityExecutionResult execute(CapabilityExecutionRequest request) {
        requireRequest(request);
        CapabilityExecutionResult result = switch (request.capability().getType()) {
            case REACT_AGENT -> executeWithinTimeout(request, () -> executeAgent(request));
            case WORKFLOW -> executeWithinTimeout(request, () -> executeWorkflow(request));
            case TOOL -> executeTool(request);
        };
        if (tokenCountEstimator.estimate(result.content()) > request.capability().getResultTokenBudget()) {
            throw invalid("能力结果超过Application发布快照Token预算");
        }
        return result;
    }

    private CapabilityExecutionResult executeWithinTimeout(CapabilityExecutionRequest request,
                                                           Callable<CapabilityExecutionResult> execution) {
        return Mono.fromCallable(execution)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(request.capability().getTimeoutSeconds()))
                .onErrorMap(TimeoutException.class, exception ->
                        invalid("能力执行超时：" + request.capability().getCode()))
                .block();
    }

    private CapabilityExecutionResult executeAgent(CapabilityExecutionRequest request) {
        ApplicationCapabilityDefinition capability = request.capability();
        requireLockedVersion(capability);
        AgentRuntimeSnapshot agent = agentRuntimeService.resolveVersion(
                capability.getTargetId(), capability.getTargetVersionId());
        requireChecksum(capability, checksum.calculate(capability.getTargetVersionId()));
        String answer = agentChatService.chat(
                agent.agentId(), agent.agentVersionId(), requireQuery(request.inputs()), request.context());
        if (answer == null || answer.isBlank()) {
            throw invalid("ReAct Agent能力必须返回非空结果");
        }
        return new CapabilityExecutionResult(answer, agent.definition().getChatModelInstanceId(), List.of());
    }

    private CapabilityExecutionResult executeWorkflow(CapabilityExecutionRequest request) {
        ApplicationCapabilityDefinition capability = request.capability();
        requireLockedVersion(capability);
        RuntimeWorkflowVersionEntity version = workflowRuntimeRepo
                .findVersion(capability.getTargetId(), capability.getTargetVersionId())
                .orElseThrow(() -> invalid("Application锁定的WorkflowVersion不存在"));
        WorkflowDefinition definition = Objects.requireNonNull(version.getDefinitionSnapshot(), "WorkflowVersion定义不能为空");
        requireChecksum(capability, checksum.calculate(definition));
        WorkflowExecutionRequest workflowRequest = new WorkflowExecutionRequest(
                request.context().getExecutionId(), capability.getTargetId(), capability.getTargetVersionId(), Map.of("query", requireQuery(request.inputs())));
        WorkflowExecutionResult result = workflowExecutor.execute(definition, workflowRequest);
        String answer = resolveAnswer(result).orElseThrow(() -> invalid("WorkflowVersion必须输出非空response"));
        Long modelInstanceId = definition.nodes().stream()
                .filter(node -> node.config() instanceof AgentNodeConfig)
                .map(node -> (AgentNodeConfig) node.config())
                .findFirst()
                .map(config -> agentRuntimeService.resolveVersion(config.agentId(), config.agentVersionId()))
                .map(agent -> agent.definition().getChatModelInstanceId())
                .orElse(null);
        return new CapabilityExecutionResult(answer, modelInstanceId, result.traces());
    }

    private CapabilityExecutionResult executeTool(CapabilityExecutionRequest request) {
        ApplicationCapabilityDefinition capability = request.capability();
        RuntimeToolEntity toolDefinition = toolRuntimeRepo.findActiveById(capability.getTargetId())
                .orElseThrow(() -> invalid("Application锁定的Tool不存在或已停用"));
        requireChecksum(capability, checksum.calculate(toolConfiguration(toolDefinition)));
        AgentTool tool = toolRuntimeService.resolveActiveTool(capability.getTargetId());
        String invocationId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        publishTool(request.context(), tool,
                new ToolEventState(invocationId, AgentToolExecutionStatus.RUNNING, null, null));
        try {
            String result = Mono.fromCallable(() -> tool.callback().call(JSON.toJSONString(request.inputs())))
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(Duration.ofSeconds(capability.getTimeoutSeconds()));
            if (result == null || result.isBlank()) {
                throw invalid("Tool能力必须返回非空结果");
            }
            publishTool(request.context(), tool, new ToolEventState(
                    invocationId, AgentToolExecutionStatus.SUCCEEDED, elapsedMillis(startedAt), null));
            return new CapabilityExecutionResult(result, null, List.of());
        } catch (RuntimeException exception) {
            publishTool(request.context(), tool, new ToolEventState(
                    invocationId, AgentToolExecutionStatus.FAILED,
                    elapsedMillis(startedAt), TOOL_EXECUTION_FAILED_CODE));
            throw exception;
        }
    }

    private static JSONObject toolConfiguration(RuntimeToolEntity tool) {
        JSONObject configuration = new JSONObject();
        configuration.put("toolCode", tool.getCode());
        configuration.put("sourceType", tool.getSourceType());
        configuration.put("mcpServerId", tool.getMcpServerId());
        configuration.put("remoteToolName", tool.getRemoteToolName());
        configuration.put("inputSchema", tool.getParameterSchema());
        return configuration;
    }

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

    private static String requireQuery(JSONObject inputs) {
        String query = inputs.getString("query");
        if (query == null || query.isBlank()) {
            throw invalid("Agent或Workflow能力缺少query输入");
        }
        return query.trim();
    }

    private static Optional<String> resolveAnswer(WorkflowExecutionResult result) {
        Object response = result.outputs().get("response");
        return response instanceof String answer && !answer.isBlank() ? Optional.of(answer) : Optional.empty();
    }

    private static void requireRequest(CapabilityExecutionRequest request) {
        if (request == null || request.capability() == null || request.capability().getType() == null
                || request.capability().getTargetId() == null || request.inputs() == null || request.context() == null) {
            throw invalid("能力执行请求不完整");
        }
        if (request.capability().getTimeoutSeconds() == null || request.capability().getTimeoutSeconds() < 1
                || request.capability().getResultTokenBudget() == null
                || request.capability().getResultTokenBudget() < 1) {
            throw invalid("能力运行预算不完整");
        }
    }

    private static void requireLockedVersion(ApplicationCapabilityDefinition capability) {
        if (capability.getTargetVersionId() == null) {
            throw invalid("能力缺少锁定目标版本");
        }
    }

    private static void requireChecksum(ApplicationCapabilityDefinition capability, String actualChecksum) {
        if (!Objects.equals(capability.getConfigurationChecksum(), actualChecksum)) {
            throw invalid("能力配置与Application发布快照不一致：" + capability.getCode());
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

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
