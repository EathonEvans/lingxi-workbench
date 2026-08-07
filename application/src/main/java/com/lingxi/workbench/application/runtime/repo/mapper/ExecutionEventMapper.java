package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** 执行时间线事件Mapper。 */
@Mapper
public interface ExecutionEventMapper extends BaseMapper<ExecutionEventEntity> {
}
