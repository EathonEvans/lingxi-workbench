package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.IntentRoutingSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 澄清会话持久化语义仓库。 */
@Repository
@RequiredArgsConstructor
public class IntentRoutingSessionRepo {

    private final IntentRoutingSessionMapper mapper;

    public Optional<IntentRoutingSessionEntity> findPending(
            Long conversationId,
            Long userId,
            Long applicationVersionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<IntentRoutingSessionEntity>lambdaQuery()
                        .eq(IntentRoutingSessionEntity::getConversationId, conversationId)
                        .eq(IntentRoutingSessionEntity::getUserId, userId)
                        .eq(IntentRoutingSessionEntity::getApplicationVersionId, applicationVersionId)
                        .eq(IntentRoutingSessionEntity::getStatus, IntentRoutingSessionStatus.PENDING)
                        .orderByDesc(IntentRoutingSessionEntity::getId)
                        .last("LIMIT 1")));
    }

    public Optional<IntentRoutingSessionEntity> findOwned(Long id, Long conversationId, Long userId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<IntentRoutingSessionEntity>lambdaQuery()
                        .eq(IntentRoutingSessionEntity::getId, id)
                        .eq(IntentRoutingSessionEntity::getConversationId, conversationId)
                        .eq(IntentRoutingSessionEntity::getUserId, userId)));
    }

    public void insert(IntentRoutingSessionEntity entity) {
        mapper.insert(entity);
    }

    public void update(IntentRoutingSessionEntity entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
    }
}
