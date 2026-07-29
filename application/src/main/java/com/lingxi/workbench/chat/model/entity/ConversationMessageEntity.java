package com.lingxi.workbench.chat.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 会话消息数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_conversation_message")
public class ConversationMessageEntity extends TimeEntity {
    /** 消息主键。 */
    private Long id;
    /** 所属会话主键。 */
    private Long conversationId;
    /** 客户端消息 ID，用于幂等和去重。 */
    private String clientMessageId;
    /** 会话内单调递增序号。 */
    private Integer sequenceNo;
    /** 消息角色：USER / ASSISTANT。 */
    private MessageRole role;
    /** 消息正文内容。 */
    private String content;
    /** 消息状态：GENERATING / COMPLETED / FAILED / CANCELLED / INTERRUPTED。 */
    private MessageStatus status;
    /** 使用的模型实例主键。 */
    private Long modelInstanceId;
    /** 本轮执行的 Application 稳定主键。 */
    private Long applicationId;
    /** 本轮冻结的 ApplicationVersion 主键。 */
    private Long applicationVersionId;
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
    /** 消息完成时间。 */
    private OffsetDateTime completedAt;
}
