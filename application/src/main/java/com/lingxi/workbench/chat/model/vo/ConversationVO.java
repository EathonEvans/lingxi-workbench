package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;

import java.time.OffsetDateTime;

/** 会话列表响应，主键使用字符串传输以避免前端精度丢失。 */
public record ConversationVO(
        String id,
        String title,
        ConversationStatus status,
        Integer messageCount,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
