package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;

import java.time.OffsetDateTime;

/** 一次 Application 顶级执行的版本、归属与可恢复状态。 */
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
    private Long orchestratorAgentId;
    private Long orchestratorAgentVersionId;
    private Long supervisorModelInstanceId;
    private Long supervisorPromptVersionId;
    private String capabilityCode;
    private String executionMode;
    private ExecutionRunStatus status;
    private String currentStage;
    private Integer revision;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
