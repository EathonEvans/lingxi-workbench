package com.lingxi.workbench.chat.memory.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 会话滚动摘要的不可变持久化快照。 */
@Data
@TableName("lingxi_core.ai_conversation_summary_snapshot")
public class ConversationSummarySnapshotEntity {

    private Long id;
    /** 会话逻辑外键。 */
    private Long conversationId;
    /** 会话所属用户主键，用于审计和范围查询。 */
    private Long userId;
    /** 触发摘要的 ApplicationVersion 逻辑外键。 */
    private Long applicationVersionId;
    /** 摘要覆盖到的最后用户消息序号。 */
    private Integer coveredUserSequenceNo;
    /** 本次生成的完整摘要。 */
    private String summary;
    /** 已发布摘要 PromptVersion 逻辑外键。 */
    private Long promptVersionId;
    /** 摘要 Chat 模型实例逻辑外键。 */
    private Long modelInstanceId;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
