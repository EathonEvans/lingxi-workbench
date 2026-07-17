package com.lingxi.workbench.chat.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
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

    /**
     * 按消息序号倒序分页查询，前端可按页向上加载历史消息。
     */
    public PageResult<ConversationMessageEntity> findPage(Long conversationId, PageQuery query) {
        IPage<ConversationMessageEntity> page = mapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), Wrappers.<ConversationMessageEntity>lambdaQuery().eq(ConversationMessageEntity::getConversationId, conversationId).orderByDesc(ConversationMessageEntity::getSequenceNo));
        return PageResult.convert(page);
    }

    /**
     * 判断会话是否存在尚未结束的模型回答。
     */
    public boolean existsGenerating(Long conversationId) {
        return mapper.exists(Wrappers.<ConversationMessageEntity>lambdaQuery().eq(ConversationMessageEntity::getConversationId, conversationId).eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT).eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING));
    }

    /**
     * 判断客户端消息标识是否已经使用。
     */
    public boolean existsClientMessageId(Long conversationId, String clientMessageId) {
        return mapper.exists(Wrappers.<ConversationMessageEntity>lambdaQuery().eq(ConversationMessageEntity::getConversationId, conversationId).eq(ConversationMessageEntity::getClientMessageId, clientMessageId));
    }

    public int nextSequence(Long conversationId) {
        return mapper.selectMaxSequence(conversationId) + 1;
    }

    /**
     * 查询当前轮之前最近的完整问答轮次。
     */
    public List<ConversationMessageEntity> findRecentCompletedRounds(Long conversationId, Integer beforeSequenceNo, int maxRounds) {
        return mapper.selectRecentCompletedRounds(conversationId, beforeSequenceNo, maxRounds);
    }

    public void save(ConversationMessageEntity entity) {
        mapper.insert(entity);
    }

    /**
     * 查询用户所属的 Assistant 消息。
     */
    public Optional<ConversationMessageEntity> findOwnedAssistantMessage(Long messageId, Long userId) {
        return mapper.selectOwnedAssistantMessage(messageId, userId);
    }

    /**
     * 只允许尚在生成中的 Assistant 消息进入终态，避免并发覆盖。
     */
    public boolean finish(
            Long id,
            MessageStatus status,
            String content,
            String errorCode,
            String errorMessage,
            MessageTokenUsageBO tokenUsage,
            OffsetDateTime now) {
        ConversationMessageEntity update = new ConversationMessageEntity();
        update.setStatus(status);
        update.setContent(content);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setInputTokens(tokenUsage.inputTokens());
        update.setOutputTokens(tokenUsage.outputTokens());
        update.setTotalTokens(tokenUsage.totalTokens());
        update.setCompletedAt(now);
        return mapper.update(update, Wrappers.<ConversationMessageEntity>lambdaUpdate().eq(ConversationMessageEntity::getId, id).eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT).eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING)) == 1;
    }
}
