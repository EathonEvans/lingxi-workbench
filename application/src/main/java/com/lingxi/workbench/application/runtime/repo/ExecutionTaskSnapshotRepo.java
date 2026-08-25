package com.lingxi.workbench.application.runtime.repo;

import com.lingxi.workbench.application.runtime.model.entity.ExecutionTaskSnapshotEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionTaskSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** AgentScope任务快照的追加持久化语义。 */
@Repository
@RequiredArgsConstructor
public class ExecutionTaskSnapshotRepo {

    private final ExecutionTaskSnapshotMapper mapper;

    public void insert(ExecutionTaskSnapshotEntity snapshot) {
        mapper.insert(snapshot);
    }
}
