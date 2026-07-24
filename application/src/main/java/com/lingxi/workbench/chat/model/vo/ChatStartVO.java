package com.lingxi.workbench.chat.model.vo;

/**
 * 流式对话开始事件，向前端返回本次持久化生成的稳定标识。
 *
 * @param conversationId    会话主键
 * @param userMessageId     用户消息主键
 * @param assistantMessageId Assistant 消息主键
 */
public record ChatStartVO(
        /** 会话主键。 */
        String conversationId,
        /** 用户消息主键。 */
        String userMessageId,
        /** Assistant 消息主键。 */
        String assistantMessageId) {
}
