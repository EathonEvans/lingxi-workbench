package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;

import java.time.OffsetDateTime;

/** 会话消息响应，所有关联主键均使用字符串传输。 */
public record ConversationMessageVO(
        String id,
        String conversationId,
        Integer sequenceNo,
        MessageRole role,
        String content,
        MessageStatus status,
        String modelInstanceId,
        String errorCode,
        String errorMessage,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {}
