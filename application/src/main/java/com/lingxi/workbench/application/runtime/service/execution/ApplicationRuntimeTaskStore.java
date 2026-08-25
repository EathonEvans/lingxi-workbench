package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus;
import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStore;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionTaskSnapshotCommand;
import io.agentscope.core.state.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 将 AgentScope Task 与能力调用尝试追加为 Application 运行事实。 */
@Component
@RequiredArgsConstructor
public class ApplicationRuntimeTaskStore implements RuntimeTaskStore {

    private final ExecutionPersistenceService persistenceService;

    @Override
    public void saveTasks(String runId, int revision, List<Task> tasks) {
        persistenceService.appendTaskSnapshot(new ExecutionTaskSnapshotCommand(runId, revision, tasks));
    }

    @Override
    public void saveInvocation(AgentInvocationState invocation) {
        persistenceService.appendStepAttempt(ExecutionStepAttemptCommand.builder()
                .runId(invocation.getRunId())
                .taskRevision(invocation.getTaskRevision())
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
