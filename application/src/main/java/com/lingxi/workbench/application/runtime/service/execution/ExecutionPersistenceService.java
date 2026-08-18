package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.agentruntime.model.SkillActivation;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;

import java.util.List;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;

/**
 * Plan-and-Execute运行数据的事务写入与恢复边界。
 */
public interface ExecutionPersistenceService {

    void startRun(ExecutionRunStartCommand command);

    void changeRunStatus(String runId, ExecutionRunStatus expected, ExecutionRunStatus target, String currentStage);

    void appendPlan(ExecutionPlanSnapshotCommand command);

    void appendStepAttempt(ExecutionStepAttemptCommand command);

    /** 记录一次 Agent 调用实际激活的 SkillVersion。 */
    void recordActivatedSkills(String runId, Long agentVersionId, List<SkillActivation> skills);

    ExecutionEventEntity appendEvent(ExecutionEventCommand command);

    /** 返回当前 Run 已持久化的最后事件序号；尚无事件时返回0。 */
    long lastEventSequence(String runId);

    List<ExecutionPlanSnapshotEntity> findPlans(String runId);

    List<ExecutionStepAttemptEntity> findStepAttempts(String runId);

    List<ExecutionEventEntity> findEventsAfter(String runId, long afterSequence);
}
