package com.lingxi.workbench.workflow.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEventEntity;
import com.lingxi.workbench.workflow.repo.mapper.WorkflowRunEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Workflow 可恢复运行事件的追加式持久化语义。 */
@Repository
@RequiredArgsConstructor
public class WorkflowRunEventRepo {

    private final WorkflowRunEventMapper mapper;

    public void insert(WorkflowRunEventEntity event) {
        mapper.insert(event);
    }

    public long nextSequence(String runId) {
        WorkflowRunEventEntity latest = mapper.selectOne(Wrappers.<WorkflowRunEventEntity>lambdaQuery()
                .eq(WorkflowRunEventEntity::getRunId, runId)
                .orderByDesc(WorkflowRunEventEntity::getSequenceNo)
                .last("LIMIT 1"));
        return Optional.ofNullable(latest).map(WorkflowRunEventEntity::getSequenceNo).map(sequence -> sequence + 1L).orElse(1L);
    }

    public List<WorkflowRunEventEntity> findAfter(String runId, long afterSequence) {
        return mapper.selectList(Wrappers.<WorkflowRunEventEntity>lambdaQuery()
                .eq(WorkflowRunEventEntity::getRunId, runId)
                .gt(WorkflowRunEventEntity::getSequenceNo, afterSequence)
                .orderByAsc(WorkflowRunEventEntity::getSequenceNo));
    }
}
