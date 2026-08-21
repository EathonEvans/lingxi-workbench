package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus;
import com.lingxi.workbench.agentruntime.plan.RuntimePlanStore;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 将 Runtime 计划和隔离步骤状态追加到 Application 事实表。 */
@Component
@RequiredArgsConstructor
public class ApplicationRuntimePlanStore implements RuntimePlanStore {

    private final ExecutionPersistenceService persistenceService;

    @Override
    public void savePlan(String runId, int planNo, PlannerDecision decision) {
        persistenceService.appendPlan(new ExecutionPlanSnapshotCommand(runId, planNo, decision));
    }

    @Override
    public void saveInvocation(AgentInvocationState invocation) {
        persistenceService.appendStepAttempt(ExecutionStepAttemptCommand.builder()
                .runId(invocation.getRunId())
                .planNo(invocation.getPlanNo())
                .attemptNo(invocation.getAttemptNo())
                .step(invocation.getStep())
                .status(status(invocation.getStatus()))
                .resultContent(invocation.getResult())
                .errorCode(invocation.getErrorCode())
                .startedAt(OffsetDateTime.ofInstant(invocation.getStartedAt(), ZoneOffset.UTC))
                .build());
    }

    private static ExecutionStepStatus status(AgentInvocationStatus status) {
        return switch (status) {
            case COMPLETED -> ExecutionStepStatus.COMPLETED;
            case FAILED -> ExecutionStepStatus.FAILED;
            case CANCELLED -> ExecutionStepStatus.CANCELLED;
            case INTERRUPTED -> ExecutionStepStatus.INTERRUPTED;
            case REUSED -> ExecutionStepStatus.REUSED;
            case RUNNING -> throw new IllegalArgumentException("步骤尝试只能追加终态");
        };
    }
}
