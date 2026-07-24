package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 会话列表响应，主键使用字符串传输以避免前端精度丢失。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {
    /** 会话主键。 */
    private String id;
    /** 会话标题。 */
    private String title;
    /** 会话状态。 */
    private ConversationStatus status;
    /** 消息总数。 */
    private Integer messageCount;
    /** 最近一条消息时间。 */
    private OffsetDateTime lastMessageAt;
    /** 创建时间。 */
    private OffsetDateTime createdAt;
    /** 更新时间。 */
    private OffsetDateTime updatedAt;
}
