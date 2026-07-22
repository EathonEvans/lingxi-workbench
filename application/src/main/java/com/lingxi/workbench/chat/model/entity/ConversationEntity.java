package com.lingxi.workbench.chat.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 用户会话数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_conversation")
public class ConversationEntity extends TimeEntity {
    /** 会话主键。 */
    private Long id;
    /** 所属用户主键。 */
    private Long userId;
    /** 会话标题。 */
    private String title;
    /** 会话状态：ACTIVE / ARCHIVED。 */
    private ConversationStatus status;
    /** 消息总数。 */
    private Integer messageCount;
    /** 最近一条消息时间，用于列表排序。 */
    private OffsetDateTime lastMessageAt;
    /** 软删除时间，删除后保留追溯能力。 */
    private OffsetDateTime deletedAt;
}
