package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
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
}
