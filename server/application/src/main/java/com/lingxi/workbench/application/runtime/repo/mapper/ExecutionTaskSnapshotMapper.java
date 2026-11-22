package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionTaskSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/** AgentScope任务快照 Mapper。 */
@Mapper
public interface ExecutionTaskSnapshotMapper extends BaseMapper<ExecutionTaskSnapshotEntity> {
}
