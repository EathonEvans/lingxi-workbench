package com.lingxi.workbench.chat.model.bo;

/**
 * 一次用户提问和模型回答对应的消息标识。
 *
 * @param conversationId    会话主键
 * @param userMessageId     用户消息主键
 * @param assistantMessageId Assistant 消息主键
 * @param userSequenceNo    用户消息序号
 */
public record ChatTurnBO(
        /** 会话主键。 */
        Long conversationId,
        /** 用户消息主键。 */
        Long userMessageId,
        /** Assistant 消息主键。 */
        Long assistantMessageId,
        /** 用户消息序号。 */
        Integer userSequenceNo) {}
