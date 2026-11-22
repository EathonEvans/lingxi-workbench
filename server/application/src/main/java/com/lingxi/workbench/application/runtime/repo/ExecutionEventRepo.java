package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 可恢复执行时间线的追加式持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionEventRepo {

    private final ExecutionEventMapper mapper;

    public void insert(ExecutionEventEntity event) {
        mapper.insert(event);
    }

    public List<ExecutionEventEntity> findAfter(String runId, long afterSequence) {
        return mapper.selectList(Wrappers.<ExecutionEventEntity>lambdaQuery()
                .eq(ExecutionEventEntity::getRunId, runId)
                .gt(ExecutionEventEntity::getSequenceNo, afterSequence)
                .orderByAsc(ExecutionEventEntity::getSequenceNo));
    }

    public Optional<ExecutionEventEntity> findBySequence(String runId, long sequenceNo) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ExecutionEventEntity>lambdaQuery()
                .eq(ExecutionEventEntity::getRunId, runId)
                .eq(ExecutionEventEntity::getSequenceNo, sequenceNo)));
    }

    public long nextSequence(String runId) {
        return lastSequence(runId) + 1L;
    }

    public long lastSequence(String runId) {
        ExecutionEventEntity latest = mapper.selectOne(Wrappers.<ExecutionEventEntity>lambdaQuery()
                .eq(ExecutionEventEntity::getRunId, runId)
                .orderByDesc(ExecutionEventEntity::getSequenceNo)
                .last("LIMIT 1"));
        return latest == null ? 0L : latest.getSequenceNo();
    }
}
