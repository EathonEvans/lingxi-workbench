package com.lingxi.workbench.workflow.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Workflow 运行 Mapper。 */
@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunEntity> {

    /** 锁定运行行，串行分配事件序号并收敛终态。 */
    @Select("SELECT * FROM lingxi_core.ai_workflow_run WHERE run_id = #{runId} FOR UPDATE")
    WorkflowRunEntity selectByRunIdForUpdate(@Param("runId") String runId);
}
