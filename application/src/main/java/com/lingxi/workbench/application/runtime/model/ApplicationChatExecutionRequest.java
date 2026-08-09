package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.context.LoginUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Application 单轮 Chat 执行所需的服务端可信输入。
 *
 * <p>由 ChatStreamServiceImpl 组装后传入 ApplicationChatExecutionService.execute()。
 * 所有字段均为服务端可信数据，不可由客户端直接构造。</p>
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationChatExecutionRequest {

    /**
     * 本轮冻结的 Application 发布快照，包含不可变的 definition、memory、intentBinding 等。
     */
    private final AppRuntimeSnapshot application;
    /**
     * 用户原始问题，已由 Controller 层 trim 处理。
     */
    private final String query;
    /**
     * 已认证的当前用户。
     */
    private final LoginUser loginUser;
    /**
     * 已落库的对话轮次，包含 conversationId / userMessageId / assistantMessageId / userSequenceNo。
     */
    private final ChatTurnBO turn;
    /**
     * 上一轮澄清事件的可选结构化响应。首次对话时为 null。
     */
    private final ClarificationResponseRequest clarificationResponse;
    /**
     * SSE 事件发布器，执行过程中实时推送 chunk / tool / planning / clarification 事件。
     */
    private final ChatExecutionEventPublisher eventPublisher;
}
