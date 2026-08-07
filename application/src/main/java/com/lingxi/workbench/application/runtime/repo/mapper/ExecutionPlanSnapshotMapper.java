package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/** Planner决策快照Mapper。 */
@Mapper
public interface ExecutionPlanSnapshotMapper extends BaseMapper<ExecutionPlanSnapshotEntity> {
}
