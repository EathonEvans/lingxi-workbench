package com.lingxi.workbench.admin.agent.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import com.lingxi.workbench.admin.agent.repo.mapper.AgentDraftKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** Agent 草稿知识库绑定数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AgentDraftKnowledgeRepo {
    private final AgentDraftKnowledgeMapper mapper;

    /** 按草稿 ID 查询知识库绑定。 */
    public Optional<AgentDraftKnowledgeEntity> findByDraftId(Long draftId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentDraftKnowledgeEntity>lambdaQuery()
                        .eq(AgentDraftKnowledgeEntity::getAgentDraftId, draftId)));
    }

    /** 插入或更新知识库绑定。 */
    public void save(AgentDraftKnowledgeEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /** 移除草稿的知识库绑定（取消关联时调用）。 */
    public void deleteByDraftId(Long draftId) {
        mapper.delete(Wrappers.<AgentDraftKnowledgeEntity>lambdaQuery()
                .eq(AgentDraftKnowledgeEntity::getAgentDraftId, draftId));
    }
}
