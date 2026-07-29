package com.lingxi.workbench.admin.agent.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionKnowledgeEntity;
import com.lingxi.workbench.admin.agent.repo.mapper.AgentVersionKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Agent 发布版本知识库绑定数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AgentVersionKnowledgeRepo {
    private final AgentVersionKnowledgeMapper mapper;

    /** 按版本 ID 查询知识库绑定索引。 */
    public Optional<AgentVersionKnowledgeEntity> findByVersionId(Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentVersionKnowledgeEntity>lambdaQuery()
                        .eq(AgentVersionKnowledgeEntity::getAgentVersionId, versionId)));
    }

    public void insert(AgentVersionKnowledgeEntity entity) {
        mapper.insert(entity);
    }
}
