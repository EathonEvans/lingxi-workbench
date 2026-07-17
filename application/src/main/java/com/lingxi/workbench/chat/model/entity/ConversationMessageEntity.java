package com.lingxi.workbench.chat.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 会话消息数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_conversation_message")
public class ConversationMessageEntity extends AuditTimeEntity {
    private Long id;
    private Long conversationId;
    private String clientMessageId;
    private Integer sequenceNo;
    private MessageRole role;
    private String content;
    private MessageStatus status;
    private Long modelInstanceId;
    private String errorCode;
    private String errorMessage;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private OffsetDateTime completedAt;
}
