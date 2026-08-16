package com.lingxi.workbench.agentexecute.loop;

import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolApprovalGate;
import com.lingxi.workbench.infra.ai.tool.AgentToolApprovalPermit;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentToolEventPublisher;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolApprovalRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolErrorConstants.TOOL_EXECUTION_FAILED_CODE;

/**
 * 为 Spring AI ToolCallback 增加结果大小边界和项目级结构化日志。
 *
 * <p>参数解析、Schema 生成与 Tool 消息协议仍全部由 Spring AI 负责。</p>
 */
@Slf4j
public final class BoundedToolCallback implements ToolCallback {

    private final AgentTool tool;
    private final AgentToolExecutionContext executionContext;
    private final int maxResultCharacters;
    private final AgentToolEventPublisher eventPublisher;
    private final AgentLoopState loopState;
    private final AgentToolApprovalGate approvalGate;

    public BoundedToolCallback(AgentTool tool, AgentToolExecutionContext executionContext, int maxResultCharacters, AgentToolEventPublisher eventPublisher,
                               AgentLoopState loopState, AgentToolApprovalGate approvalGate) {
        this.tool = tool;
        this.executionContext = executionContext;
        this.maxResultCharacters = maxResultCharacters;
        this.eventPublisher = eventPublisher;
        this.loopState = loopState;
        this.approvalGate = approvalGate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return tool.callback().getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return tool.callback().getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return execute(toolInput, () -> tool.callback().call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return execute(toolInput, () -> tool.callback().call(toolInput, toolContext));
    }

    private String execute(String toolInput, Supplier<String> invocation) {
        String invocationId = UUID.randomUUID().toString();
        Optional<String> reusableResult = loopState.toolLedger().findReusableResult(tool.definition(), toolInput);
        if (reusableResult.isPresent()) {
            loopState.requestFinalResponse("确定性工具已复用相同结果");
            publishToolEvent(invocationId, AgentToolExecutionStatus.REUSED, 0L, null);
            log.info("Agent工具复用已有结果，executionId={}，toolCode={}", executionContext.getExecutionId(), getToolDefinition().name());
            return loopState.toolLedger().reuseResponse();
        }
        long startedAt = System.nanoTime();
        AgentToolApprovalPermit permit = approvalGate.authorize(AgentToolApprovalRequest.builder()
                .definition(tool.definition())
                .executionContext(executionContext)
                .toolInput(toolInput)
                .build());
        publishToolEvent(invocationId, AgentToolExecutionStatus.RUNNING, null, null);
        String result;
        try {
            result = invocation.get();
        } catch (RuntimeException exception) {
            permit.fail(exception);
            long durationMillis = elapsedMillis(startedAt);
            publishToolEvent(invocationId, AgentToolExecutionStatus.FAILED, durationMillis, TOOL_EXECUTION_FAILED_CODE);
            log.warn("Agent工具执行失败，executionId={}，toolCode={}，durationMs={}", executionContext.getExecutionId(), getToolDefinition().name(), durationMillis, exception);
            throw exception;
        }

        // 外部副作用已经发生后，持久化失败必须保留 RUNNING 待对账，不能再伪装成调用失败并自动重放。
        permit.complete(result);
        if (Objects.isNull(result)) {
            throw new AgentToolException("工具返回结果不能为空：" + getToolDefinition().name());
        }
        if (result.length() > maxResultCharacters) {
            throw new AgentToolException("工具返回结果超过字符上限：" + getToolDefinition().name() + "，limit=" + maxResultCharacters);
        }
        loopState.toolLedger().recordSuccessfulResult(tool.definition(), toolInput, result);
        if (tool.shouldFinalizeAfterSuccess()) {
            loopState.requestFinalResponse("工具结果已达到有效信息收敛边界");
        }
        long durationMillis = elapsedMillis(startedAt);
        publishToolEvent(invocationId, AgentToolExecutionStatus.SUCCEEDED, durationMillis, null);
        log.info("Agent工具执行完成，executionId={}，toolCode={}，resultCharacters={}，durationMs={}", executionContext.getExecutionId(), getToolDefinition().name(), result.length(), durationMillis);
        return result;
    }

    private void publishToolEvent(String invocationId, AgentToolExecutionStatus status, Long durationMillis, String errorCode) {
        eventPublisher.publish(AgentToolExecutionEvent.builder()
                .invocationId(invocationId)
                .toolCode(tool.definition().getCode())
                .displayName(tool.definition().getDisplayName())
                .status(status)
                .durationMillis(durationMillis)
                .errorCode(errorCode)
                .build());
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
