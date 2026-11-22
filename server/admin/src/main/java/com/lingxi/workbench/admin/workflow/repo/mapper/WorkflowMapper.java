package com.lingxi.workbench.admin.workflow.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

/** Workflow 主记录 Mapper。 */
@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {
}
