package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.ConversationTurnStartBO;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionMetaBO;
import com.lingxi.workbench.chat.model.bo.StartConversationTurnBO;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.model.request.RenameConversationRequest;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import com.lingxi.workbench.chat.model.vo.ConversationVO;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.Optional;

/**
 * 用户会话及消息业务。
 */
public interface ConversationService {

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
     * 在同一事务中创建本轮用户消息和 Assistant 占位消息。
     *
     * <p>首次发送会幂等创建会话，已有会话则锁定用户所属记录；随后校验会话可写、
     * 没有其他生成中回答，并使用客户端消息标识防止重复提交。</p>
     *
     * @param input 首次创建或继续已有会话的一轮消息输入
     * @return 消息标识以及本次是否真正创建，用于阻止幂等重试重复启动模型
     */
    ConversationTurnStartBO startTurn(StartConversationTurnBO input);

    /**
     * 将 Assistant 消息更新为完整回答。
     */
    void completeAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage,
                           AssistantCompletionMetaBO metadata);

    /** 查询事务消息回查所需的 Assistant 终态。 */
    Optional<MessageStatus> findAssistantCompletionStatus(Long conversationId, Long assistantMessageId, Long applicationVersionId);

    /**
     * 记录模型调用失败及已经生成的部分回答。
     */
    void failAssistant(Long assistantMessageId, String content, String errorCode, String errorMessage, TokenUsage tokenUsage);

    /**
     * 记录客户端取消及已经生成的部分回答。
     */
    void cancelAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage);

    /**
     * 记录服务进程中断造成的未完成回答。
     */
    void interruptAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage);

    /** 将当前生成任务挂起为等待人工决策。 */
    void waitForHuman(Long assistantMessageId, String content, TokenUsage tokenUsage);

    /** 人工批准后原子恢复同一 Assistant 消息。 */
    void resumeFromHuman(Long assistantMessageId);

    /** 拒绝或过期时取消等待中的 Assistant 消息。 */
    void cancelWaiting(Long assistantMessageId, String code, String message);
}
