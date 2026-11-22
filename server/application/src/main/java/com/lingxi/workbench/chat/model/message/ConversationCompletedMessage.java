package com.lingxi.workbench.chat.model.message;

import java.util.Objects;

/**
 * 会话轮次完成事务事件，只携带消费者重新定位业务数据所需的稳定标识。
 *
 * @param eventId             确定性事件标识，重复发送时保持不变
 * @param conversationId      会话主键
 * @param assistantMessageId  Assistant 消息主键，同时作为当前轮次稳定标识
 * @param appVersionId        本轮冻结的 ApplicationVersion 主键
 */
public record ConversationCompletedMessage(
        String eventId,
        Long conversationId,
        Long assistantMessageId,
        Long appVersionId) {

    public ConversationCompletedMessage {
        if (Objects.isNull(eventId) || eventId.isBlank()) {
            throw new IllegalArgumentException("会话事务事件标识不能为空");
        }
        Objects.requireNonNull(conversationId, "会话主键不能为空");
        Objects.requireNonNull(assistantMessageId, "Assistant消息主键不能为空");
        Objects.requireNonNull(appVersionId, "ApplicationVersion主键不能为空");
    }
}
