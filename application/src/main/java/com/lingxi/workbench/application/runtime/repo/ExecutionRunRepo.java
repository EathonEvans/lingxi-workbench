package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Application 顶级运行元数据持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionRunRepo {

    private final ExecutionRunMapper mapper;

    public void insert(ExecutionRunEntity run) {
        mapper.insert(run);
    }

    public Optional<ExecutionRunEntity> findByRunId(String runId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ExecutionRunEntity>lambdaQuery()
                .eq(ExecutionRunEntity::getRunId, runId)));
    }

    public Optional<ExecutionRunEntity> findByAssistantMessageId(Long assistantMessageId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ExecutionRunEntity>lambdaQuery()
                .eq(ExecutionRunEntity::getAssistantMessageId, assistantMessageId)));
    }

    public Optional<ExecutionRunEntity> lockByRunId(String runId) {
        return Optional.ofNullable(mapper.selectByRunIdForUpdate(runId));
    }

    public boolean changeStatus(String runId, ExecutionRunStatus expected, ExecutionRunStatus target,
                                String currentStage, OffsetDateTime completedAt) {
        ExecutionRunEntity update = new ExecutionRunEntity();
        update.setStatus(target);
        update.setCurrentStage(currentStage);
        update.setCompletedAt(completedAt);
        return mapper.update(update, Wrappers.<ExecutionRunEntity>lambdaUpdate()
                .eq(ExecutionRunEntity::getRunId, runId)
                .eq(ExecutionRunEntity::getStatus, expected)
                .setSql("revision = revision + 1")) == 1;
    }

    /** 判断 RocketMQ 事务对应的运行状态是否已经提交。 */
    public boolean hasStatus(String runId, ExecutionRunStatus... statuses) {
        return mapper.exists(Wrappers.<ExecutionRunEntity>lambdaQuery()
                .eq(ExecutionRunEntity::getRunId, runId)
                .in(ExecutionRunEntity::getStatus, (Object[]) statuses));
    }
}
