package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityInvocationScopeTest {

    @Test
    void shouldKeepOnlyInvocationIsolationFacts() {
        ChatExecutionEventPublisher publisher = ChatExecutionEventPublisher.noop();

        CapabilityInvocationScope scope = new CapabilityInvocationScope(
                "run-1:task:1:step-1:attempt:1", ExecutionScope.INVOCATION, publisher);

        assertEquals("run-1:task:1:step-1:attempt:1", scope.agentSessionId());
        assertEquals(ExecutionScope.INVOCATION, scope.executionScope());
        assertEquals(publisher, scope.eventPublisher());
    }

    @Test
    void shouldRejectIncompleteScope() {
        assertThrows(IllegalArgumentException.class,
                () -> new CapabilityInvocationScope(" ", ExecutionScope.INVOCATION,
                        ChatExecutionEventPublisher.noop()));
    }
}
