package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;

import java.util.List;

/**
 * Plan-and-Execute运行数据的事务写入与恢复边界。
 */
public interface ExecutionPersistenceService {

    void startRun(ExecutionRunStartCommand command);

    void appendPlan(ExecutionPlanSnapshotCommand command);

    void appendStepAttempt(ExecutionStepAttemptCommand command);

    ExecutionEventEntity appendEvent(ExecutionEventCommand command);

    List<ExecutionPlanSnapshotEntity> findPlans(String runId);

    List<ExecutionStepAttemptEntity> findStepAttempts(String runId);

    List<ExecutionEventEntity> findEventsAfter(String runId, long afterSequence);
}
