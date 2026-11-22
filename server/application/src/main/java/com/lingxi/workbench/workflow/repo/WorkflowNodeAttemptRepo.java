package com.lingxi.workbench.workflow.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.workflow.model.entity.WorkflowNodeAttemptEntity;
import com.lingxi.workbench.workflow.repo.mapper.WorkflowNodeAttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Workflow 节点终态尝试的追加式持久化语义。 */
@Repository
@RequiredArgsConstructor
public class WorkflowNodeAttemptRepo {

    private final WorkflowNodeAttemptMapper mapper;

    public void insert(WorkflowNodeAttemptEntity attempt) {
        mapper.insert(attempt);
    }

    public List<WorkflowNodeAttemptEntity> findByRunId(String runId) {
        return mapper.selectList(Wrappers.<WorkflowNodeAttemptEntity>lambdaQuery()
                .eq(WorkflowNodeAttemptEntity::getRunId, runId)
                .orderByAsc(WorkflowNodeAttemptEntity::getStartedAt));
    }
}
