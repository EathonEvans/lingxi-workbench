package com.lingxi.workbench.workflow.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** Workflow 运行事件 Mapper。 */
@Mapper
public interface WorkflowRunEventMapper extends BaseMapper<WorkflowRunEventEntity> {
}
