package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionStepAttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 追加式步骤尝试持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionStepAttemptRepo {

    private final ExecutionStepAttemptMapper mapper;

    public void insert(ExecutionStepAttemptEntity attempt) {
        mapper.insert(attempt);
    }

    public List<ExecutionStepAttemptEntity> findByRunId(String runId) {
        return mapper.selectList(Wrappers.<ExecutionStepAttemptEntity>lambdaQuery()
                .eq(ExecutionStepAttemptEntity::getRunId, runId)
                .orderByAsc(ExecutionStepAttemptEntity::getPlanNo)
                .orderByAsc(ExecutionStepAttemptEntity::getId));
    }
}
