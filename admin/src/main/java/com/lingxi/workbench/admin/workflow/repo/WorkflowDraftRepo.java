package com.lingxi.workbench.admin.workflow.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowDraftEntity;
import com.lingxi.workbench.admin.workflow.repo.mapper.WorkflowDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    /** 带行锁查询草稿，用于发布。 */
    public Optional<WorkflowDraftEntity> findByWorkflowIdForUpdate(Long workflowId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<WorkflowDraftEntity>lambdaQuery()
                        .eq(WorkflowDraftEntity::getWorkflowId, workflowId)
                        .last("FOR UPDATE")));
    }

    /** 乐观锁更新草稿，revision 必须匹配。 */
    public boolean isUpdatedWithRevision(WorkflowDraftEntity entity, Long expectedRevision) {
        entity.setRevision(expectedRevision + 1);
        entity.setUpdatedAt(OffsetDateTime.now());
        return mapper.update(entity, Wrappers.<WorkflowDraftEntity>lambdaUpdate()
                .eq(WorkflowDraftEntity::getId, entity.getId())
                .eq(WorkflowDraftEntity::getRevision, expectedRevision)) == 1;
    }

    /** 发布成功后更新草稿基线版本和 revision。 */
    public void updatePublicationBase(Long draftId, Long baseVersionId, Long operatorId, Long expectedRevision) {
        int affected = mapper.update(null, Wrappers.<WorkflowDraftEntity>lambdaUpdate()
                .set(WorkflowDraftEntity::getBaseVersionId, baseVersionId)
                .set(WorkflowDraftEntity::getUpdatedBy, operatorId)
                .set(WorkflowDraftEntity::getRevision, expectedRevision + 1)
                .set(WorkflowDraftEntity::getUpdatedAt, OffsetDateTime.now())
                .eq(WorkflowDraftEntity::getId, draftId)
                .eq(WorkflowDraftEntity::getRevision, expectedRevision));
        if (affected != 1) {
            throw new IllegalStateException("Workflow草稿发布基线更新失败");
        }
    }

    public void save(WorkflowDraftEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
