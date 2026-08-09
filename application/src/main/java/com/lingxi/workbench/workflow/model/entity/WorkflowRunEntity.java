package com.lingxi.workbench.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunStatus;
import lombok.Data;

import java.time.OffsetDateTime;

/** 单次 Workflow 运行及其锁定版本和用户归属。 */
@Data
@TableName("lingxi_core.ai_workflow_run")
public class WorkflowRunEntity {

    private Long id;
    private String runId;
    private String executionId;
    private Long assistantMessageId;
    private Long conversationId;
    private Long userId;
    private Long applicationId;
    private Long applicationVersionId;
    private Long workflowId;
    private Long workflowVersionId;
    private WorkflowRunStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String errorCode;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
