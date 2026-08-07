package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.InteractionSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Planner澄清与风险确认会话的持久化语义。 */
@Repository
@RequiredArgsConstructor
public class InteractionSessionRepo {

    private final InteractionSessionMapper mapper;

    public void insert(InteractionSessionEntity session) {
        mapper.insert(session);
    }

    public void update(InteractionSessionEntity session) {
        mapper.updateById(session);
    }

    public Optional<InteractionSessionEntity> findOwned(Long id, Long conversationId, Long userId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getId, id)
                .eq(InteractionSessionEntity::getConversationId, conversationId)
                .eq(InteractionSessionEntity::getUserId, userId)));
    }

    public Optional<InteractionSessionEntity> findPending(Long conversationId, Long userId, Long applicationVersionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getConversationId, conversationId)
                .eq(InteractionSessionEntity::getUserId, userId)
                .eq(InteractionSessionEntity::getApplicationVersionId, applicationVersionId)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .last("LIMIT 1")));
    }
}
