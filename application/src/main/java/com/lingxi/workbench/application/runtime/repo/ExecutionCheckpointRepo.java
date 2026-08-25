package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionCheckpointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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

    /** 查询仍可恢复但协议已落后的检查点，供发布切换时按批收敛。 */
    public List<ExecutionCheckpointEntity> findActiveBeforeVersion(int currentVersion, int limit) {
        if (currentVersion < 1 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("检查点协议版本或扫描批次不合法");
        }
        return mapper.selectList(Wrappers.<ExecutionCheckpointEntity>lambdaQuery()
                .eq(ExecutionCheckpointEntity::getStatus, ExecutionCheckpointStatus.ACTIVE)
                .lt(ExecutionCheckpointEntity::getCheckpointVersion, currentVersion)
                .orderByAsc(ExecutionCheckpointEntity::getCreatedAt)
                .last("LIMIT " + limit));
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
