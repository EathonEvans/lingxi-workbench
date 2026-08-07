package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeWorkflowVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** WorkflowVersion 运行端只读 Mapper。 */
@Mapper
public interface RuntimeWorkflowVersionMapper extends BaseMapper<RuntimeWorkflowVersionEntity> {
}
