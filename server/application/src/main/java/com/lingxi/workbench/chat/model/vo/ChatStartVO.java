package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.application.runtime.model.vo.ApplicationAssistantIdentityVO;

/**
 * 流式对话开始事件，向前端返回本次持久化生成的稳定标识。
 *
 * @param conversationId     会话主键
 * @param userMessageId      用户消息主键
 * @param assistantMessageId Assistant 消息主键
 * @param assistant          本轮冻结的应用助手身份
 */
public record ChatStartVO(
        String conversationId,
        String userMessageId,
        String assistantMessageId,
        ApplicationAssistantIdentityVO assistant) {
}
