package com.lingxi.workbench.chat.model.bo;

/**
 * 会话轮次持久化结果。
 *
 * @param turn      已持久化的消息标识
 * @param isCreated 本次是否新建了消息；false 表示命中客户端幂等请求
 */
public record ConversationTurnStartBO(ChatTurnBO turn, boolean isCreated) {
}
