package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerStep;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationRuntimePlanStoreTest {

    @Test
    void shouldAppendTypedPlanAndCompletedInvocation() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ApplicationRuntimePlanStore store = new ApplicationRuntimePlanStore(persistenceService);
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_CAPABILITY)
                .capabilityCode("research")
                .build();
        AgentInvocationState invocation = invocation();
        invocation.complete("研究完成");

        store.savePlan("run-1", 2, decision);
        store.saveInvocation(invocation);

        ArgumentCaptor<ExecutionPlanSnapshotCommand> planCaptor =
                ArgumentCaptor.forClass(ExecutionPlanSnapshotCommand.class);
        verify(persistenceService).appendPlan(planCaptor.capture());
        assertEquals(2, planCaptor.getValue().planNo());
        assertEquals(decision, planCaptor.getValue().decision());
        ArgumentCaptor<ExecutionStepAttemptCommand> stepCaptor =
                ArgumentCaptor.forClass(ExecutionStepAttemptCommand.class);
        verify(persistenceService).appendStepAttempt(stepCaptor.capture());
        assertEquals(ExecutionStepStatus.COMPLETED, stepCaptor.getValue().getStatus());
        assertEquals("研究完成", stepCaptor.getValue().getResultContent());
    }

    @Test
    void shouldRejectRunningInvocationBecauseAttemptFactsAreAppendOnlyTerminal() {
        ApplicationRuntimePlanStore store = new ApplicationRuntimePlanStore(mock(ExecutionPersistenceService.class));

        assertThrows(IllegalArgumentException.class, () -> store.saveInvocation(invocation()));
    }

    private static AgentInvocationState invocation() {
        PlannerStep step = PlannerStep.builder().id("research-step").name("研究")
                .capabilityCode("research").dependsOn(List.of()).build();
        RuntimeCapability capability = RuntimeCapability.builder().code("research").name("研究")
                .type(RuntimeCapabilityType.AGENT).risk(RuntimeCapabilityRisk.READ_ONLY)
                .maxCalls(1).resultTokenBudget(100).build();
        return new AgentInvocationState("run-1", 2, 1, step, capability,
                Map.of("query", "问题"), "fingerprint");
    }
}
