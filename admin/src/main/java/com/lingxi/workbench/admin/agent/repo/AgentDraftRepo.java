package com.lingxi.workbench.admin.agent.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import com.lingxi.workbench.admin.agent.repo.mapper.AgentDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Agent 草稿数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AgentDraftRepo {
    private final AgentDraftMapper mapper;

    /** 按 Agent ID 查询草稿。 */
    public Optional<AgentDraftEntity> findByAgentId(Long agentId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentDraftEntity>lambdaQuery()
                        .eq(AgentDraftEntity::getAgentId, agentId)));
    }

    /** 带行锁按 Agent ID 查询草稿，用于 saveDraft 等写操作。 */
    public Optional<AgentDraftEntity> findByAgentIdForUpdate(Long agentId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentDraftEntity>lambdaQuery()
                        .eq(AgentDraftEntity::getAgentId, agentId)
                        .last("FOR UPDATE")));
    }

    /** 批量按 Agent ID 列表查询草稿，用于列表页填充 revision。 */
    public List<AgentDraftEntity> findAllByAgentIds(List<Long> agentIds) {
        if (agentIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<AgentDraftEntity>lambdaQuery()
                .in(AgentDraftEntity::getAgentId, agentIds));
    }

    public void insert(AgentDraftEntity entity) {
        mapper.insert(entity);
    }

    /** 以 revision 乐观锁更新草稿字段，避免并发覆盖。 */
    public boolean isUpdatedWithRevision(AgentDraftEntity entity, Long expectedRevision) {
        return mapper.isUpdatedWithRevision(entity, expectedRevision) == 1;
    }

    /** 发布后同步更新草稿基线版本号和 revision。 */
    public void updatePublicationBase(
            Long draftId, Long baseVersionId, Long operatorId, Long expectedRevision) {
        int affected = mapper.update(
                null,
                Wrappers.<AgentDraftEntity>lambdaUpdate()
                        .set(AgentDraftEntity::getBaseVersionId, baseVersionId)
                        .set(AgentDraftEntity::getUpdatedBy, operatorId)
                        .set(AgentDraftEntity::getRevision, expectedRevision + 1)
                        .set(AgentDraftEntity::getUpdatedAt, OffsetDateTime.now())
                        .eq(AgentDraftEntity::getId, draftId)
                        .eq(AgentDraftEntity::getRevision, expectedRevision));
        if (affected != 1) {
            throw new IllegalStateException("Agent草稿发布基线更新失败");
        }
    }
}
