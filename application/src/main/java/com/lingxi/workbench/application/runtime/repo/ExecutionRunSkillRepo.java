package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunSkillEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionRunSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 执行 Skill 事实数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ExecutionRunSkillRepo {

    private final ExecutionRunSkillMapper mapper;

    public void insert(ExecutionRunSkillEntity entity) {
        mapper.insert(entity);
    }

    public boolean existsByEvent(String runId, long eventSequenceNo) {
        return mapper.selectCount(Wrappers.<ExecutionRunSkillEntity>lambdaQuery()
                .eq(ExecutionRunSkillEntity::getRunId, runId)
                .eq(ExecutionRunSkillEntity::getEventSequenceNo, eventSequenceNo)) > 0;
    }
}
