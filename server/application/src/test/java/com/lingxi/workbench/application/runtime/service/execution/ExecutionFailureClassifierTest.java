package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeLimitException;
import com.lingxi.workbench.application.runtime.model.ExecutionFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionFailureClassifierTest {

    private final ExecutionFailureClassifier classifier = new ExecutionFailureClassifier();

    @Test
    void shouldExposeReconciliationPendingAsNonRetryableFailure() {
        ExecutionFailure failure = classifier.classify(
                new AgentReconciliationPendingException(new RuntimeException("checkpoint write failed")));

        assertEquals("TOOL_RECONCILIATION_PENDING", failure.code());
        assertFalse(failure.retryable());
    }

    @Test
    void shouldNotRetryUnknownExecutionFailure() {
        ExecutionFailure failure = classifier.classify(new IllegalStateException("provider failed"));

        assertEquals("CAPABILITY_EXECUTION_FAILED", failure.code());
        assertEquals("本次处理未完成，请稍后重试", failure.clientMessage());
        assertFalse(failure.retryable());
    }

    @Test
    void shouldNotRetryRuntimeLimitWithinTheSameRun() {
        ExecutionFailure failure = classifier.classify(
                new AgentRuntimeLimitException("AGENT_MODEL_CALL_LIMIT", "Agent工作模型调用次数已达到上限"));

        assertEquals("AGENT_MODEL_CALL_LIMIT", failure.code());
        assertEquals("本轮处理步骤已达到上限，请缩小问题范围后重试", failure.clientMessage());
        assertFalse(failure.retryable());
    }

    @Test
    void shouldHideInternalTokenLimitWording() {
        ExecutionFailure failure = classifier.classify(
                new AgentRuntimeLimitException("AGENT_TOKEN_LIMIT", "Agent Token额度已达到上限"));

        assertEquals("AGENT_TOKEN_LIMIT", failure.code());
        assertEquals("本轮可用额度已用完，请缩小问题范围后重试", failure.clientMessage());
        assertFalse(failure.retryable());
    }
}
