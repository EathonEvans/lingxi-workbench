package com.lingxi.workbench.workflow.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.workflow.model.entity.WorkflowNodeAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/** Workflow 节点尝试 Mapper。 */
@Mapper
public interface WorkflowNodeAttemptMapper extends BaseMapper<WorkflowNodeAttemptEntity> {
}
