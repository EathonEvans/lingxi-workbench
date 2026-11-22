package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.IntentRoutingSessionMapper;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 意图澄清会话的持久化 Repo。
 *
 * <p>提供以下查询能力：</p>
 * <ul>
 *   <li>按会话 ID + 用户 ID 查询指定会话并校验归属</li>
 *   <li>按对话 ID + 用户 ID + ApplicationVersion 查询当前唯一的 PENDING 会话</li>
 *   <li>插入新会话和更新已有会话状态</li>
 * </ul>
 *
 * <p>PENDING 会话按时间降序返回最新的一条，确保同一次对话中始终处理
 * 最近创建的澄清请求。历史会话的状态变更不影响新会话的创建。</p>
 */
@Repository
@RequiredArgsConstructor
public class IntentRoutingSessionRepo {

    private final IntentRoutingSessionMapper mapper;

    /**
     * 按会话 ID 查询归属于指定用户和对话的会话；状态由恢复入口检查。
     * 用于用户提交结构化澄清响应时恢复已有的澄清上下文。
     *
     * @param id             会话主键
     * @param conversationId 对话 ID，校验归属
     * @param userId         用户 ID，校验归属
     * @return 匹配的会话，未找到时为空
     */
    public Optional<IntentRoutingSessionEntity> findOwned(Long id, Long conversationId, Long userId) {
        return Optional.ofNullable(
                mapper.selectOne(new LambdaQueryWrapper<IntentRoutingSessionEntity>()
                        .eq(IntentRoutingSessionEntity::getId, id)
                        .eq(IntentRoutingSessionEntity::getConversationId, conversationId)
                        .eq(IntentRoutingSessionEntity::getUserId, userId)));
    }

    /**
     * 查找指定对话 + 用户 + ApplicationVersion 下当前唯一的 PENDING 会话。
     * 用于在无需提供 clarificationId 的纯文本回复中自动恢复澄清上下文。
     *
     * <p>多条 PENDING 会话时按创建时间降序取最新的。</p>
     *
     * @param conversationId       对话 ID
     * @param userId               用户 ID
     * @param applicationVersionId Application 版本 ID
     * @return 最新的 PENDING 会话，无 PENDING 会话时为空
     */
    public Optional<IntentRoutingSessionEntity> findPending(
            Long conversationId, Long userId, Long applicationVersionId) {
        return Optional.ofNullable(
                mapper.selectOne(new LambdaQueryWrapper<IntentRoutingSessionEntity>()
                        .eq(IntentRoutingSessionEntity::getConversationId, conversationId)
                        .eq(IntentRoutingSessionEntity::getUserId, userId)
                        .eq(IntentRoutingSessionEntity::getApplicationVersionId, applicationVersionId)
                        .eq(IntentRoutingSessionEntity::getStatus, IntentRoutingSessionStatus.PENDING)
                        .orderByDesc(IntentRoutingSessionEntity::getCreatedAt)
                        .last("LIMIT 1")));
    }

    /**
     * 插入新的澄清会话。
     *
     * @param session 包含完整信息的实体，主键由数据库自增生成
     */
    public void insert(IntentRoutingSessionEntity session) {
        if (mapper.insert(session) != 1) {
            throw new IllegalStateException("意图澄清会话写入失败");
        }
    }

    /** 仅推进本轮读取的 PENDING 状态；未命中时明确拒绝，交由上层事务回滚决策记录。 */
    public void updatePending(IntentRoutingSessionEntity session, IntentClarificationType expectedType, int expectedRound) {
        if (mapper.update(session, new LambdaQueryWrapper<IntentRoutingSessionEntity>()
                .eq(IntentRoutingSessionEntity::getId, session.getId())
                .eq(IntentRoutingSessionEntity::getConversationId, session.getConversationId())
                .eq(IntentRoutingSessionEntity::getUserId, session.getUserId())
                .eq(IntentRoutingSessionEntity::getApplicationVersionId, session.getApplicationVersionId())
                .eq(IntentRoutingSessionEntity::getIntentTreeVersionId, session.getIntentTreeVersionId())
                .eq(IntentRoutingSessionEntity::getStatus, IntentRoutingSessionStatus.PENDING)
                .eq(IntentRoutingSessionEntity::getClarificationType, expectedType)
                .eq(IntentRoutingSessionEntity::getRoundNo, expectedRound)) != 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话已经推进，请按最新问题回复");
        }
    }
}
