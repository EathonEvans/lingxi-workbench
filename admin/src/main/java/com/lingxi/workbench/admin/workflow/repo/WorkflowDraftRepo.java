package com.lingxi.workbench.admin.workflow.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowDraftEntity;
import com.lingxi.workbench.admin.workflow.repo.mapper.WorkflowDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** Workflow 草稿数据仓库。 */
@Repository
@RequiredArgsConstructor
public class WorkflowDraftRepo {
    private final WorkflowDraftMapper mapper;

    /** 按 workflowId 查询草稿。 */
    public Optional<WorkflowDraftEntity> findByWorkflowId(Long workflowId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<WorkflowDraftEntity>lambdaQuery()
                        .eq(WorkflowDraftEntity::getWorkflowId, workflowId)));
    }

    /** 乐观锁更新草稿，revision 必须匹配。 */
    public boolean updateWithOptimisticLock(WorkflowDraftEntity entity) {
        Long expectedRevision = entity.getRevision();
        return mapper.update(entity, Wrappers.<WorkflowDraftEntity>lambdaUpdate()
                .eq(WorkflowDraftEntity::getId, entity.getId())
                .eq(WorkflowDraftEntity::getRevision, expectedRevision)) > 0;
    }

    public void save(WorkflowDraftEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
