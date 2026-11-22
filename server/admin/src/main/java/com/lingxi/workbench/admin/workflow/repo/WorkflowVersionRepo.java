package com.lingxi.workbench.admin.workflow.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowVersionEntity;
import com.lingxi.workbench.admin.workflow.repo.mapper.WorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Workflow 版本数据仓库。 */
@Repository
@RequiredArgsConstructor
public class WorkflowVersionRepo {
    private final WorkflowVersionMapper mapper;

    /** 按 workflowId 查询版本列表，按版本号倒序。 */
    public List<WorkflowVersionEntity> findByWorkflowId(Long workflowId) {
        return mapper.selectList(Wrappers.<WorkflowVersionEntity>lambdaQuery()
                .eq(WorkflowVersionEntity::getWorkflowId, workflowId)
                .orderByDesc(WorkflowVersionEntity::getVersionNo));
    }

    /** 查询下一个版本号。 */
    public int nextVersionNo(Long workflowId) {
        return Math.toIntExact(mapper.selectCount(Wrappers.<WorkflowVersionEntity>lambdaQuery()
                .eq(WorkflowVersionEntity::getWorkflowId, workflowId)) + 1);
    }

    /** 按 ID 查询版本。 */
    public Optional<WorkflowVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按 Workflow 和版本主键查询，防止跨 Workflow 引用。 */
    public Optional<WorkflowVersionEntity> findByWorkflowIdAndId(Long workflowId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<WorkflowVersionEntity>lambdaQuery()
                .eq(WorkflowVersionEntity::getWorkflowId, workflowId)
                .eq(WorkflowVersionEntity::getId, versionId)));
    }

    public void save(WorkflowVersionEntity entity) {
        mapper.insert(entity);
    }
}
