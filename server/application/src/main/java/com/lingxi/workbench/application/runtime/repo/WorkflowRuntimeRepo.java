package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeWorkflowVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeWorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 独立 Workflow 正式版本的运行端只读仓储。 */
@Repository
@RequiredArgsConstructor
public class WorkflowRuntimeRepo {

    private final RuntimeWorkflowVersionMapper mapper;

    public Optional<RuntimeWorkflowVersionEntity> findVersion(Long workflowId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<RuntimeWorkflowVersionEntity>lambdaQuery()
                        .eq(RuntimeWorkflowVersionEntity::getWorkflowId, workflowId)
                        .eq(RuntimeWorkflowVersionEntity::getId, versionId)));
    }
}
