package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.model.request.RenameConversationRequest;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import com.lingxi.workbench.chat.model.vo.ConversationVO;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;

/**
 * 用户会话及消息业务。
 */
public interface ConversationService {

    /**
     * 创建空会话。
     */
    ConversationVO create(Long userId);

    /**
     * 分页查询当前用户会话。
     */
    PageResult<ConversationVO> page(Long userId, ConversationPageQuery query);

    /**
     * 分页查询指定会话的消息历史。
     */
    PageResult<ConversationMessageVO> messagePage(Long userId, Long conversationId, PageQuery query);

    /**
     * 修改会话标题。
     */
    void rename(Long userId, Long conversationId, RenameConversationRequest request);

    /**
     * 归档会话。
     */
    void archive(Long userId, Long conversationId);

    /**
     * 软删除会话。
     */
    void delete(Long userId, Long conversationId);

    /**
     * 创建本轮用户消息和 Assistant 占位消息。
     */
    ChatTurnBO startTurn(Long userId, Long conversationId, String clientMessageId, String prompt, Long modelInstanceId);

    /**
     * 将 Assistant 消息更新为完整回答。
     */
    void completeAssistant(
            Long assistantMessageId, String content, MessageTokenUsageBO tokenUsage);

    /**
     * 记录模型调用失败及已经生成的部分回答。
     */
    void failAssistant(
            Long assistantMessageId,
            String content,
            String errorCode,
            String errorMessage,
            MessageTokenUsageBO tokenUsage);

    /**
     * 记录客户端取消及已经生成的部分回答。
     */
    void cancelAssistant(
            Long assistantMessageId, String content, MessageTokenUsageBO tokenUsage);

    /**
     * 记录服务进程中断造成的未完成回答。
     */
    void interruptAssistant(
            Long assistantMessageId, String content, MessageTokenUsageBO tokenUsage);
}
