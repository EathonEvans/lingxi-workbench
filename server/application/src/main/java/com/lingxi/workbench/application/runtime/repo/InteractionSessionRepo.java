package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.InteractionDecision;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.InteractionSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Planner 澄清与 HITL 交互会话的持久化语义。 */
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

    public Optional<InteractionSessionEntity> findOwned(Long id, Long userId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getId, id)
                .eq(InteractionSessionEntity::getUserId, userId)));
    }

    public Optional<InteractionSessionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 只查询仍在等待人工决策的检查点关联会话，避免覆盖已提交的审批结果。 */
    public Optional<InteractionSessionEntity> findPendingByCheckpointId(Long checkpointId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getCheckpointId, checkpointId)
                .eq(InteractionSessionEntity::getInteractionType, InteractionSessionType.ACTION_APPROVAL)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .last("LIMIT 1")));
    }

    public Optional<InteractionSessionEntity> findAnyPending(Long conversationId, Long userId,
                                                             Long applicationVersionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getConversationId, conversationId)
                .eq(InteractionSessionEntity::getUserId, userId)
                .eq(InteractionSessionEntity::getApplicationVersionId, applicationVersionId)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .last("LIMIT 1")));
    }

    public Optional<InteractionSessionEntity> findPending(Long conversationId, Long userId, Long applicationVersionId,
                                                          InteractionSessionType type) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getConversationId, conversationId)
                .eq(InteractionSessionEntity::getUserId, userId)
                .eq(InteractionSessionEntity::getApplicationVersionId, applicationVersionId)
                .eq(InteractionSessionEntity::getInteractionType, type)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .last("LIMIT 1")));
    }

    /** 查询当前用户在指定会话中的待处理交互，供刷新后恢复卡片。 */
    public List<InteractionSessionEntity> findPendingOwned(Long conversationId, Long userId,
                                                           InteractionSessionType type) {
        return mapper.selectList(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getConversationId, conversationId)
                .eq(InteractionSessionEntity::getUserId, userId)
                .eq(InteractionSessionEntity::getInteractionType, type)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .orderByAsc(InteractionSessionEntity::getCreatedAt));
    }

    public Optional<InteractionSessionEntity> findPendingOwnedByAssistant(Long assistantMessageId, Long userId,
                                                                          InteractionSessionType type) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getAssistantMessageId, assistantMessageId)
                .eq(InteractionSessionEntity::getUserId, userId)
                .eq(InteractionSessionEntity::getInteractionType, type)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .last("LIMIT 1")));
    }

    /** 按交互类型查询已过期但尚未决策的会话，避免不同生命周期互相越界处理。 */
    public List<InteractionSessionEntity> findExpiredPending(InteractionSessionType type, OffsetDateTime now, int limit) {
        return mapper.selectList(Wrappers.<InteractionSessionEntity>lambdaQuery()
                .eq(InteractionSessionEntity::getInteractionType, type)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .le(InteractionSessionEntity::getExpiresAt, now)
                .orderByAsc(InteractionSessionEntity::getExpiresAt)
                .last("LIMIT " + limit));
    }

    public boolean decide(Long id, Long userId, int expectedRevision, InteractionSessionStatus target,
                          InteractionDecision decision, Long decidedBy, String idempotencyKey,
                          OffsetDateTime decidedAt) {
        InteractionSessionEntity update = new InteractionSessionEntity();
        update.setStatus(target);
        update.setRevision(expectedRevision + 1);
        update.setDecision(decision.name());
        update.setDecidedBy(decidedBy);
        update.setIdempotencyKey(idempotencyKey);
        update.setDecidedAt(decidedAt);
        return mapper.update(update, Wrappers.<InteractionSessionEntity>lambdaUpdate()
                .eq(InteractionSessionEntity::getId, id)
                .eq(InteractionSessionEntity::getUserId, userId)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .eq(InteractionSessionEntity::getRevision, expectedRevision)) == 1;
    }

    public boolean complete(Long id) {
        InteractionSessionEntity update = new InteractionSessionEntity();
        update.setStatus(InteractionSessionStatus.COMPLETED);
        return mapper.update(update, Wrappers.<InteractionSessionEntity>lambdaUpdate()
                .eq(InteractionSessionEntity::getId, id)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.RESOLVED)
                .setSql("revision = revision + 1")) == 1;
    }

    /** 过期任务只允许从待处理状态推进，避免覆盖已经作出的用户决策。 */
    public boolean expire(Long id, int revision) {
        InteractionSessionEntity update = new InteractionSessionEntity();
        update.setStatus(InteractionSessionStatus.EXPIRED);
        update.setRevision(revision + 1);
        return mapper.update(update, Wrappers.<InteractionSessionEntity>lambdaUpdate()
                .eq(InteractionSessionEntity::getId, id)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .eq(InteractionSessionEntity::getRevision, revision)) == 1;
    }

    /** 检查点协议升级时终止待处理审批，不伪造用户的拒绝或取消决策。 */
    public boolean failForCheckpointProtocol(Long id, int revision) {
        InteractionSessionEntity update = new InteractionSessionEntity();
        update.setStatus(InteractionSessionStatus.FAILED);
        update.setRevision(revision + 1);
        return mapper.update(update, Wrappers.<InteractionSessionEntity>lambdaUpdate()
                .eq(InteractionSessionEntity::getId, id)
                .eq(InteractionSessionEntity::getStatus, InteractionSessionStatus.PENDING)
                .eq(InteractionSessionEntity::getRevision, revision)) == 1;
    }
}
