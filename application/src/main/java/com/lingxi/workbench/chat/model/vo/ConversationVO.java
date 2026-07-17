package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;

import java.time.OffsetDateTime;

/** 会话列表响应，主键使用字符串传输以避免前端精度丢失。 */
public record ConversationVO(
        /** 会话主键。 */
        String id,
        /** 会话标题。 */
        String title,
        /** 会话状态。 */
        ConversationStatus status,
        /** 消息总数。 */
        Integer messageCount,
        /** 最近一条消息时间。 */
        OffsetDateTime lastMessageAt,
        /** 创建时间。 */
        OffsetDateTime createdAt,
        /** 更新时间。 */
        OffsetDateTime updatedAt) {}
