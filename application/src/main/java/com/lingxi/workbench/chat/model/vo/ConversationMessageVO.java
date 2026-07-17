package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;

import java.time.OffsetDateTime;

/** 会话消息响应，所有关联主键均使用字符串传输。 */
public record ConversationMessageVO(
        /** 消息主键。 */
        String id,
        /** 所属会话主键。 */
        String conversationId,
        /** 会话内消息序号。 */
        Integer sequenceNo,
        /** 消息角色。 */
        MessageRole role,
        /** 消息正文内容。 */
        String content,
        /** 消息状态。 */
        MessageStatus status,
        /** 使用的模型实例主键。 */
        String modelInstanceId,
        /** 错误码。 */
        String errorCode,
        /** 错误消息。 */
        String errorMessage,
        /** 输入 Token 数。 */
        Integer inputTokens,
        /** 输出 Token 数。 */
        Integer outputTokens,
        /** 总 Token 数。 */
        Integer totalTokens,
        /** 创建时间。 */
        OffsetDateTime createdAt,
        /** 消息完成时间。 */
        OffsetDateTime completedAt) {}
