package com.lingxi.workbench.admin.evaluation.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.evaluation.model.entity.EvaluationRunEntity;
import org.apache.ibatis.annotations.Mapper;

/** 评测任务数据库映射。 */
@Mapper
public interface EvaluationRunMapper extends BaseMapper<EvaluationRunEntity> {
}
