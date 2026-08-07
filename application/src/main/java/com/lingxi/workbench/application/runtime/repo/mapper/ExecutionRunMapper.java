package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Plan-and-Execute运行元数据Mapper。 */
@Mapper
public interface ExecutionRunMapper extends BaseMapper<ExecutionRunEntity> {

    /** 锁定运行元数据行，以串行分配持久化事件序号。 */
    @Select("SELECT * FROM lingxi_core.ai_execution_run WHERE run_id = #{runId} FOR UPDATE")
    ExecutionRunEntity selectByRunIdForUpdate(@Param("runId") String runId);
}
