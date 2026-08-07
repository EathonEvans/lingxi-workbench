package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 一次Plan-and-Execute运行的不可变版本与归属元数据。 */
@Data
@TableName("lingxi_core.ai_execution_run")
public class ExecutionRunEntity {

    private Long id;
    private String runId;
    private Long assistantMessageId;
    private Long conversationId;
    private Long userId;
    private Long applicationId;
    private Long applicationVersionId;
    private Long plannerModelInstanceId;
    private Long plannerPromptVersionId;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
