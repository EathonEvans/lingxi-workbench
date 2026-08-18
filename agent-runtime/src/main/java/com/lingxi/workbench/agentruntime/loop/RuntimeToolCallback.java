package com.lingxi.workbench.agentruntime.loop;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
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

/** 使用唯一 ExecutionState 装饰 Spring AI ToolCallback 的执行边界。 */
@Slf4j
public final class RuntimeToolCallback implements ToolCallback {

    private final AgentTool tool;
    private final AgentToolExecutionContext executionContext;
    private final int maxResultCharacters;
    private final ExecutionState state;

    public RuntimeToolCallback(AgentTool tool, AgentToolExecutionContext executionContext,
                               int maxResultCharacters, ExecutionState state) {
        this.tool = tool;
        this.executionContext = executionContext;
        this.maxResultCharacters = maxResultCharacters;
        this.state = state;
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
        Optional<Long> approvedInvocationId = state.consumeApprovedInvocation(tool.definition(), toolInput);
        Optional<String> reusableResult = state.findReusableToolResult(tool.definition(), toolInput);
        if (reusableResult.isPresent()) {
            state.toolReused(invocationId, tool.definition());
            state.requestFinalization("确定性工具已复用相同结果");
            if (approvedInvocationId.isPresent()) {
                try {
                    state.completeApprovedInvocation(approvedInvocationId.get(), reusableResult.get());
                } catch (RuntimeException exception) {
                    state.reconciliationPending(approvedInvocationId.get(), exception);
                    throw new AgentReconciliationPendingException(exception);
                }
            }
            log.info("Agent工具复用已有结果，executionId={}，toolCode={}",
                    executionContext.getExecutionId(), getToolDefinition().name());
            return state.toolReuseResponse();
        }
        long startedAt = System.nanoTime();
        state.toolStarted(invocationId, tool.definition());
        String result;
        try {
            result = invocation.get();
        } catch (RuntimeException exception) {
            approvedInvocationId.ifPresent(id -> state.failApprovedInvocation(id, exception));
            long durationMillis = elapsedMillis(startedAt);
            state.toolFailed(invocationId, tool.definition(), durationMillis, TOOL_EXECUTION_FAILED_CODE);
            log.warn("Agent工具执行失败，executionId={}，toolCode={}，durationMs={}",
                    executionContext.getExecutionId(), getToolDefinition().name(), durationMillis, exception);
            throw exception;
        }

        try {
            validateResult(result);
            if (approvedInvocationId.isPresent()) {
                state.completeApprovedInvocation(approvedInvocationId.get(), result);
            }
        } catch (RuntimeException exception) {
            if (approvedInvocationId.isPresent()) {
                state.reconciliationPending(approvedInvocationId.get(), exception);
                throw new AgentReconciliationPendingException(exception);
            }
            throw exception;
        }
        state.recordSuccessfulToolResult(tool.definition(), toolInput, result);
        long durationMillis = elapsedMillis(startedAt);
        state.toolCompleted(invocationId, tool.definition(), durationMillis);
        if (tool.shouldFinalizeAfterSuccess()) {
            state.requestFinalization("工具结果已达到有效信息收敛边界");
        }
        log.info("Agent工具执行完成，executionId={}，toolCode={}，resultCharacters={}，durationMs={}",
                executionContext.getExecutionId(), getToolDefinition().name(), result.length(), durationMillis);
        return result;
    }

    private void validateResult(String result) {
        if (Objects.isNull(result)) {
            throw new AgentToolException("工具返回结果不能为空：" + getToolDefinition().name());
        }
        if (result.length() > maxResultCharacters) {
            throw new AgentToolException("工具返回结果超过字符上限：" + getToolDefinition().name()
                    + "，limit=" + maxResultCharacters);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
