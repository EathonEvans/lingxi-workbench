package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionPlanSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 追加式Planner决策快照持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionPlanSnapshotRepo {

    private final ExecutionPlanSnapshotMapper mapper;

    public void insert(ExecutionPlanSnapshotEntity snapshot) {
        mapper.insert(snapshot);
    }

    public List<ExecutionPlanSnapshotEntity> findByRunId(String runId) {
        return mapper.selectList(Wrappers.<ExecutionPlanSnapshotEntity>lambdaQuery()
                .eq(ExecutionPlanSnapshotEntity::getRunId, runId)
                .orderByAsc(ExecutionPlanSnapshotEntity::getPlanNo));
    }
}
