package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    public long nextSequence(String runId) {
        ExecutionEventEntity latest = mapper.selectOne(Wrappers.<ExecutionEventEntity>lambdaQuery()
                .eq(ExecutionEventEntity::getRunId, runId)
                .orderByDesc(ExecutionEventEntity::getSequenceNo)
                .last("LIMIT 1"));
        return latest == null ? 1L : latest.getSequenceNo() + 1L;
    }
}
