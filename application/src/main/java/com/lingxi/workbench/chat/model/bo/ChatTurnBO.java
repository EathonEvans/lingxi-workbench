package com.lingxi.workbench.chat.model.bo;

/**
 * 一次用户提问和模型回答对应的消息标识。
 *
 * @param conversationId     会话主键
 * @param userMessageId      用户消息主键
 * @param assistantMessageId Assistant 消息主键
 * @param userSequenceNo     用户消息序号
 */
public record ChatTurnBO(Long conversationId, Long userMessageId, Long assistantMessageId, Integer userSequenceNo) {
}
