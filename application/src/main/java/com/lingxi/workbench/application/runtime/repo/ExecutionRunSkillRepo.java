package com.lingxi.workbench.application.runtime.repo;

import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunSkillEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.ExecutionRunSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 执行 Skill 事实数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ExecutionRunSkillRepo {

    private final ExecutionRunSkillMapper mapper;

    public void insertAll(List<ExecutionRunSkillEntity> entities) {
        for (ExecutionRunSkillEntity entity : entities) {
            mapper.insert(entity);
        }
    }
}
