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
        return mapper.exists(Wrappers.<ConversationMessageEntity>lambdaQuery().eq(ConversationMessageEntity::getConversationId, conversationId).eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT).eq(ConversationMessageEntity::getStatus, MessageStatus.GENERATING));
    }

    /**
     * 判断客户端消息标识是否已经使用，用于拦截网络重试造成的重复发送。
     */
    public boolean existsClientMessageId(Long conversationId, String clientMessageId) {
        return mapper.exists(Wrappers.<ConversationMessageEntity>lambdaQuery().eq(ConversationMessageEntity::getConversationId, conversationId).eq(ConversationMessageEntity::getClientMessageId, clientMessageId));
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
    public boolean finish(Long id, MessageStatus status, String content, String errorCode, String errorMessage, TokenUsage tokenUsage, OffsetDateTime now) {
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
