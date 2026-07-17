package com.lingxi.workbench.chat.model.bo;

/** 一次用户提问和模型回答对应的消息标识。 */
public record ChatTurnBO(
        Long conversationId,
        Long userMessageId,
        Long assistantMessageId,
        Integer userSequenceNo) {}
