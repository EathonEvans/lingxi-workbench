package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStep;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionTaskSnapshotCommand;
import io.agentscope.core.state.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationRuntimeTaskStoreTest {

    @Test
    void shouldAppendAgentScopeTasksAndCompletedInvocation() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ApplicationRuntimeTaskStore store = new ApplicationRuntimeTaskStore(persistenceService);
        Task task = Task.builder().id("research-task").subject("研究").description("研究")
                .state(Task.State.IN_PROGRESS).metadata(Map.of("capabilityCode", "research")).build();
        AgentInvocationState invocation = invocation();
        invocation.complete("研究完成");

        store.saveTasks("run-1", 2, List.of(task));
        store.saveInvocation(invocation);

        ArgumentCaptor<ExecutionTaskSnapshotCommand> taskCaptor =
                ArgumentCaptor.forClass(ExecutionTaskSnapshotCommand.class);
        verify(persistenceService).appendTaskSnapshot(taskCaptor.capture());
        assertEquals(2, taskCaptor.getValue().revision());
        assertEquals("research", taskCaptor.getValue().tasks().getFirst().getMetadata().get("capabilityCode"));
        ArgumentCaptor<ExecutionStepAttemptCommand> stepCaptor =
                ArgumentCaptor.forClass(ExecutionStepAttemptCommand.class);
        verify(persistenceService).appendStepAttempt(stepCaptor.capture());
        assertEquals(ExecutionStepStatus.COMPLETED, stepCaptor.getValue().getStatus());
        assertEquals("研究完成", stepCaptor.getValue().getResultContent());
    }

    @Test
    void shouldRejectRunningInvocationBecauseAttemptFactsAreAppendOnlyTerminal() {
        ApplicationRuntimeTaskStore store = new ApplicationRuntimeTaskStore(mock(ExecutionPersistenceService.class));

        assertThrows(IllegalArgumentException.class, () -> store.saveInvocation(invocation()));
    }

    private static AgentInvocationState invocation() {
        RuntimeTaskStep step = RuntimeTaskStep.builder().id("research-step").name("研究")
                .capabilityCode("research").build();
        RuntimeCapability capability = RuntimeCapability.builder().code("research").name("研究")
                .type(RuntimeCapabilityType.AGENT).targetId(30L).risk(RuntimeCapabilityRisk.READ_ONLY)
                .maxCalls(1).resultTokenBudget(100).build();
        return new AgentInvocationState("run-1", 2, 1, step, capability,
                Map.of("query", "问题"), "fingerprint");
    }
}
