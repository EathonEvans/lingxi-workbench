package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import org.apache.ibatis.annotations.Mapper;

/** 执行检查点 Mapper。 */
@Mapper
public interface ExecutionCheckpointMapper extends BaseMapper<ExecutionCheckpointEntity> {
}
