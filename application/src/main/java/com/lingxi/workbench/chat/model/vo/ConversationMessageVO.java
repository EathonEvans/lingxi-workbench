package com.lingxi.workbench.chat.model.vo;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 会话消息响应，所有关联主键均使用字符串传输。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageVO {
    /** 消息主键。 */
    private String id;
    /** 所属会话主键。 */
    private String conversationId;
    /** 会话内消息序号。 */
    private Integer sequenceNo;
    /** 消息角色。 */
    private MessageRole role;
    /** 消息正文内容。 */
    private String content;
    /** 消息状态。 */
    private MessageStatus status;
    /** 使用的模型实例主键。 */
    private String modelInstanceId;
    /** 错误码。 */
    private String errorCode;
    /** 错误消息。 */
    private String errorMessage;
    /** 输入 Token 数。 */
    private Integer inputTokens;
    /** 输出 Token 数。 */
    private Integer outputTokens;
    /** 总 Token 数。 */
    private Integer totalTokens;
    /** 创建时间。 */
    private OffsetDateTime createdAt;
    /** 消息完成时间。 */
    private OffsetDateTime completedAt;
}
