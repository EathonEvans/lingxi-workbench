package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Plan-and-Execute运行元数据持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionRunRepo {

    private final ExecutionRunMapper mapper;

    public void insert(ExecutionRunEntity run) {
        mapper.insert(run);
    }

    public Optional<ExecutionRunEntity> findByRunId(String runId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ExecutionRunEntity>lambdaQuery()
                .eq(ExecutionRunEntity::getRunId, runId)));
    }

    public Optional<ExecutionRunEntity> findByAssistantMessageId(Long assistantMessageId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ExecutionRunEntity>lambdaQuery()
                .eq(ExecutionRunEntity::getAssistantMessageId, assistantMessageId)));
    }

    public Optional<ExecutionRunEntity> lockByRunId(String runId) {
        return Optional.ofNullable(mapper.selectByRunIdForUpdate(runId));
    }
}
