package com.lingxi.workbench.chat.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 用户会话数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_conversation")
public class ConversationEntity extends AuditTimeEntity {
    private Long id;
    private Long userId;
    private String title;
    private ConversationStatus status;
    private Integer messageCount;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime deletedAt;
}
