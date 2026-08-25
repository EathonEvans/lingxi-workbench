package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionSkillActivationCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionTaskSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;

import java.util.List;

/**
 * AgentScope任务执行数据的事务写入与恢复边界。
 */
public interface ExecutionPersistenceService {

    void startRun(ExecutionRunStartCommand command);

    void changeRunStatus(String runId, ExecutionRunStatus expected, ExecutionRunStatus target, String currentStage);

    void appendTaskSnapshot(ExecutionTaskSnapshotCommand command);

    void appendStepAttempt(ExecutionStepAttemptCommand command);

    /** 原子追加Skill激活事件与对应运行审计事实。 */
    ExecutionEventEntity appendSkillActivationEvent(ExecutionEventCommand event,
                                                     ExecutionSkillActivationCommand activation);

    ExecutionEventEntity appendEvent(ExecutionEventCommand command);

    /** 返回当前 Run 已持久化的最后事件序号；尚无事件时返回0。 */
    long lastEventSequence(String runId);

    List<ExecutionStepAttemptEntity> findStepAttempts(String runId);

    List<ExecutionEventEntity> findEventsAfter(String runId, long afterSequence);
}
