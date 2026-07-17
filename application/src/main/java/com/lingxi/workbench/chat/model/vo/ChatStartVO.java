package com.lingxi.workbench.chat.model.vo;

/** 流式对话开始事件，向前端返回本次持久化生成的稳定标识。 */
public record ChatStartVO(
        String conversationId,
        String userMessageId,
        String assistantMessageId) {}
