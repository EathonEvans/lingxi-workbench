package com.lingxi.workbench.workflow.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunStatus;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEntity;
import com.lingxi.workbench.workflow.repo.mapper.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Workflow 运行元数据持久化语义。 */
@Repository
@RequiredArgsConstructor
public class WorkflowRunRepo {

    private final WorkflowRunMapper mapper;

    public void insert(WorkflowRunEntity run) {
        mapper.insert(run);
    }

    public Optional<WorkflowRunEntity> lockByRunId(String runId) {
        return Optional.ofNullable(mapper.selectByRunIdForUpdate(runId));
    }

    public boolean finish(String runId, WorkflowRunStatus status, OffsetDateTime finishedAt, String errorCode) {
        int affected = mapper.update(null, Wrappers.<WorkflowRunEntity>lambdaUpdate()
                .eq(WorkflowRunEntity::getRunId, runId)
                .eq(WorkflowRunEntity::getStatus, WorkflowRunStatus.RUNNING)
                .set(WorkflowRunEntity::getStatus, status)
                .set(WorkflowRunEntity::getFinishedAt, finishedAt)
                .set(WorkflowRunEntity::getErrorCode, errorCode));
        return affected == 1;
    }
}
