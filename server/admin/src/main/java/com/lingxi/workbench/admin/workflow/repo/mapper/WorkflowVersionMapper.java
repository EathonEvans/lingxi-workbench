package com.lingxi.workbench.admin.workflow.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** Workflow 版本 Mapper。 */
@Mapper
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersionEntity> {
}
