package com.lingxi.workbench.application.runtime.repo;

import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeSkillMapper;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeSkillVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Skill 运行状态和精确版本的只读仓库。 */
@Repository
@RequiredArgsConstructor
public class SkillRuntimeRepo {

    private final RuntimeSkillMapper skillMapper;
    private final RuntimeSkillVersionMapper versionMapper;

    public List<RuntimeSkillEntity> findSkills(List<Long> ids) {
        return ids.isEmpty() ? List.of() : skillMapper.selectByIds(ids);
    }

    public List<RuntimeSkillVersionEntity> findVersions(List<Long> ids) {
        return ids.isEmpty() ? List.of() : versionMapper.selectByIds(ids);
    }
}
