package com.lingxi.workbench.admin.agent.repo;

import com.lingxi.workbench.admin.agent.model.entity.AgentVersionSkillEntity;
import com.lingxi.workbench.admin.agent.repo.mapper.AgentVersionSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** AgentVersion Skill 关系数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AgentVersionSkillRepo {

    private final AgentVersionSkillMapper mapper;

    /** 在 AgentVersion 发布事务内写入锁定关系。 */
    public void insertAll(List<AgentVersionSkillEntity> entities) {
        for (AgentVersionSkillEntity entity : entities) {
            mapper.insert(entity);
        }
    }
}
