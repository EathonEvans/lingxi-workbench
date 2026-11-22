package com.lingxi.workbench.chat.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.chat.repo.mapper.ConversationMessageMapper;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会话消息数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class ConversationMessageRepo {

    private final ConversationMessageMapper mapper;

    /** 按主键查询消息。 */
    public Optional<ConversationMessageEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /**
     * 按消息序号倒序分页查询，前端可按页向上加载历史消息。
     */
    public PageResult<ConversationMessageEntity> findPage(Long conversationId, PageQuery query) {
        IPage<ConversationMessageEntity> page = mapper.selectPage(query.toPage(), Wrappers.<ConversationMessageEntity>lambdaQuery().eq(ConversationMessageEntity::getConversationId, conversationId).orderByDesc(ConversationMessageEntity::getSequenceNo));
        return PageResult.convert(page);
    }

    /**
     * 判断会话是否存在尚未结束的 Assistant 回答。
     * 该校验用于保证同一会话同一时间只运行一个模型生成任务。
     */
    public boolean existsGenerating(Long conversationId) {
        return mapper.exists(Wrappers.<ConversationMessageEntity>lambdaQuery()
                .eq(ConversationMessageEntity::getConversationId, conversationId)
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .in(ConversationMessageEntity::getStatus, MessageStatus.GENERATING, MessageStatus.WAITING_HUMAN));
    }

    /** 按客户端消息标识查询既有用户消息。 */
    public Optional<ConversationMessageEntity> findUserMessage(Long conversationId, String clientMessageId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ConversationMessageEntity>lambdaQuery()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .eq(ConversationMessageEntity::getClientMessageId, clientMessageId)
                        .eq(ConversationMessageEntity::getRole, MessageRole.USER)));
    }

    /** 按会话及序号查询一条消息。 */
    public Optional<ConversationMessageEntity> findBySequence(Long conversationId, int sequenceNo) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ConversationMessageEntity>lambdaQuery()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .eq(ConversationMessageEntity::getSequenceNo, sequenceNo)));
    }

    /** 查询事务回查所需的 Assistant 消息状态。 */
    public Optional<MessageStatus> findCompletionStatus(Long conversationId, Long assistantMessageId, Long applicationVersionId) {
        return Optional.ofNullable(mapper.selectOne(
                        Wrappers.<ConversationMessageEntity>lambdaQuery()
                                .select(ConversationMessageEntity::getStatus)
                                .eq(ConversationMessageEntity::getId, assistantMessageId)
                                .eq(ConversationMessageEntity::getConversationId, conversationId)
                                .eq(ConversationMessageEntity::getApplicationVersionId, applicationVersionId)
                                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)))
                .map(ConversationMessageEntity::getStatus);
    }

    /** 查询已经完成且版本匹配的 Assistant 消息，供 MQ 消费者重建记忆事件。 */
    public Optional<ConversationMessageEntity> findCompletedAssistant(Long conversationId, Long assistantMessageId, Long applicationVersionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ConversationMessageEntity>lambdaQuery()
                        .eq(ConversationMessageEntity::getId, assistantMessageId)
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .eq(ConversationMessageEntity::getApplicationVersionId, applicationVersionId)
                        .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                        .eq(ConversationMessageEntity::getStatus, MessageStatus.COMPLETED)));
    }

    /**
     * 获取会话下一个消息序号。
     * 调用方必须先锁定会话，避免并发请求读取到相同序号。
     */
    public int nextSequence(Long conversationId) {
        return mapper.selectMaxSequence(conversationId) + 1;
    }

    /**
     * 查询当前轮之前最近的完整问答轮次，供窗口记忆按轮数和 Token 预算继续裁剪。
     */
    public List<ConversationMessageEntity> findRecentCompletedRounds(Long conversationId, Integer beforeSequenceNo, int maxRounds) {
        return mapper.selectRecentCompletedRounds(conversationId, beforeSequenceNo, maxRounds);
    }

    /** 查询指定覆盖位置之后最早的完整问答轮次，供滚动摘要连续推进。 */
    public List<ConversationMessageEntity> findCompletedRoundsAfter(Long conversationId, int afterUserSequenceNo,
                                                                    int throughUserSequenceNo, int maxRounds) {
        return mapper.selectCompletedRoundsAfter(conversationId, afterUserSequenceNo, throughUserSequenceNo, maxRounds);
    }

    /** 扫描超过阈值仍处于生成中的Assistant占位消息。 */
    public List<ConversationMessageEntity> findStaleGenerating(OffsetDateTime before, int limit) {
        return mapper.selectList(Wrappers.<ConversationMessageEntity>lambdaQuery()
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING)
                .le(ConversationMessageEntity::getCreatedAt, before)
                .orderByAsc(ConversationMessageEntity::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /** 扫描本次服务启动后完成且尚未执行记忆事件兜底重发的消息。 */
    public List<ConversationMessageEntity> findMemoryReconcileCandidates(
            OffsetDateTime startedAfter, OffsetDateTime completedBefore, int limit) {
        return mapper.selectList(Wrappers.<ConversationMessageEntity>lambdaQuery()
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.COMPLETED)
                .isNotNull(ConversationMessageEntity::getApplicationVersionId)
                .ge(ConversationMessageEntity::getCreatedAt, startedAfter)
                .le(ConversationMessageEntity::getCompletedAt, completedBefore)
                .isNull(ConversationMessageEntity::getMemoryReconciledAt)
                .orderByAsc(ConversationMessageEntity::getCompletedAt)
                .last("LIMIT " + limit));
    }

    public long countStaleGenerating(OffsetDateTime before) {
        return mapper.selectCount(Wrappers.<ConversationMessageEntity>lambdaQuery()
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING)
                .le(ConversationMessageEntity::getCreatedAt, before));
    }

    public long countMemoryReconcileCandidates(OffsetDateTime startedAfter, OffsetDateTime completedBefore) {
        return mapper.selectCount(Wrappers.<ConversationMessageEntity>lambdaQuery()
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.COMPLETED)
                .isNotNull(ConversationMessageEntity::getApplicationVersionId)
                .ge(ConversationMessageEntity::getCreatedAt, startedAfter)
                .le(ConversationMessageEntity::getCompletedAt, completedBefore)
                .isNull(ConversationMessageEntity::getMemoryReconciledAt));
    }

    /** 成功重发后以条件更新标记，防止定时任务重复触发。 */
    public boolean markMemoryReconciled(Long id, OffsetDateTime reconciledAt) {
        ConversationMessageEntity update = new ConversationMessageEntity();
        update.setMemoryReconciledAt(reconciledAt);
        return mapper.update(update, Wrappers.<ConversationMessageEntity>lambdaUpdate()
                .eq(ConversationMessageEntity::getId, id)
                .isNull(ConversationMessageEntity::getMemoryReconciledAt)) == 1;
    }

    /**
     * 新增一条会话消息；消息内容更新统一通过明确的终态方法完成。
     */
    public void save(ConversationMessageEntity entity) {
        mapper.insert(entity);
    }

    /**
     * 联合会话表查询用户所属的 Assistant 消息，用于续接和取消前的资源归属校验。
     */
    public Optional<ConversationMessageEntity> findOwnedAssistantMessage(Long messageId, Long userId) {
        return mapper.selectOwnedAssistantMessage(messageId, userId);
    }

    /**
     * 只允许尚在生成中的 Assistant 消息进入终态，避免并发覆盖。
     * 条件更新失败时返回 false，表示该消息已经被其他完成、失败或取消流程处理。
     */
    public boolean finish(
            Long id,
            MessageStatus status,
            String content,
            String errorCode,
            String errorMessage,
            TokenUsage tokenUsage,
            Long modelInstanceId,
            OffsetDateTime now) {
        ConversationMessageEntity update = new ConversationMessageEntity();
        update.setStatus(status);
        update.setContent(content);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setInputTokens(tokenUsage.inputTokens());
        update.setOutputTokens(tokenUsage.outputTokens());
        update.setTotalTokens(tokenUsage.totalTokens());
        update.setModelInstanceId(modelInstanceId);
        update.setCompletedAt(now);
        return mapper.update(update, Wrappers.<ConversationMessageEntity>lambdaUpdate().eq(ConversationMessageEntity::getId, id).eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT).eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING)) == 1;
    }

    /** 将运行中的 Assistant 消息转为等待人工决策，保留已经生成的文本和用量。 */
    public boolean waitForHuman(Long id, String content, TokenUsage tokenUsage) {
        ConversationMessageEntity update = new ConversationMessageEntity();
        update.setStatus(MessageStatus.WAITING_HUMAN);
        update.setContent(content);
        update.setInputTokens(tokenUsage.inputTokens());
        update.setOutputTokens(tokenUsage.outputTokens());
        update.setTotalTokens(tokenUsage.totalTokens());
        return mapper.update(update, Wrappers.<ConversationMessageEntity>lambdaUpdate()
                .eq(ConversationMessageEntity::getId, id)
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING)) == 1;
    }

    /** 人工批准后原子恢复同一 Assistant 消息，防止重复消费启动多个任务。 */
    public boolean resumeFromHuman(Long id) {
        ConversationMessageEntity update = new ConversationMessageEntity();
        update.setStatus(MessageStatus.GENERATING);
        return mapper.update(update, Wrappers.<ConversationMessageEntity>lambdaUpdate()
                .eq(ConversationMessageEntity::getId, id)
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.WAITING_HUMAN)) == 1;
    }

    /** 拒绝或过期时允许等待态进入取消终态。 */
    public boolean cancelWaiting(Long id, String errorCode, String errorMessage, OffsetDateTime now) {
        ConversationMessageEntity update = new ConversationMessageEntity();
        update.setStatus(MessageStatus.CANCELLED);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setCompletedAt(now);
        return mapper.update(update, Wrappers.<ConversationMessageEntity>lambdaUpdate()
                .eq(ConversationMessageEntity::getId, id)
                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(ConversationMessageEntity::getStatus, MessageStatus.WAITING_HUMAN)) == 1;
    }
}
