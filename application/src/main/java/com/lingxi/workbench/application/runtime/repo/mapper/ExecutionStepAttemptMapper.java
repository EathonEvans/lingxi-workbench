package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/** 计划步骤尝试Mapper。 */
@Mapper
public interface ExecutionStepAttemptMapper extends BaseMapper<ExecutionStepAttemptEntity> {
}
