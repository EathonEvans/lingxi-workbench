package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/**
 * Assistant 完成事务的进程内参数，不写入 RocketMQ 消息体。
 *
 * @param event           只包含业务定位信息的事务事件
 * @param content         已生成的完整回答
 * @param tokenUsage      本轮累计 Token 用量
 * @param metadata        模型实例与非完整收口说明
 */
public record AssistantCompletionTxBO(
        ConversationCompletedMessage event,
        String content,
        TokenUsage tokenUsage,
        AssistantCompletionMetaBO metadata) {
}
