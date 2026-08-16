package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionCheckpointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 不可变执行检查点持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionCheckpointRepo {

    private final ExecutionCheckpointMapper mapper;

    public void insert(ExecutionCheckpointEntity checkpoint) {
        mapper.insert(checkpoint);
    }

    public Optional<ExecutionCheckpointEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean consume(Long id, OffsetDateTime consumedAt) {
        ExecutionCheckpointEntity update = new ExecutionCheckpointEntity();
        update.setStatus(ExecutionCheckpointStatus.CONSUMED);
        update.setConsumedAt(consumedAt);
        return mapper.update(update, Wrappers.<ExecutionCheckpointEntity>lambdaUpdate()
                .eq(ExecutionCheckpointEntity::getId, id)
                .eq(ExecutionCheckpointEntity::getStatus, ExecutionCheckpointStatus.ACTIVE)) == 1;
    }

    public boolean expire(Long id) {
        ExecutionCheckpointEntity update = new ExecutionCheckpointEntity();
        update.setStatus(ExecutionCheckpointStatus.EXPIRED);
        return mapper.update(update, Wrappers.<ExecutionCheckpointEntity>lambdaUpdate()
                .eq(ExecutionCheckpointEntity::getId, id)
                .eq(ExecutionCheckpointEntity::getStatus, ExecutionCheckpointStatus.ACTIVE)) == 1;
    }

    public boolean supersede(Long id) {
        ExecutionCheckpointEntity update = new ExecutionCheckpointEntity();
        update.setStatus(ExecutionCheckpointStatus.SUPERSEDED);
        return mapper.update(update, Wrappers.<ExecutionCheckpointEntity>lambdaUpdate()
                .eq(ExecutionCheckpointEntity::getId, id)
                .eq(ExecutionCheckpointEntity::getStatus, ExecutionCheckpointStatus.ACTIVE)) == 1;
    }
}
