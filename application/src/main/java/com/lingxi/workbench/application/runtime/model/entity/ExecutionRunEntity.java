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
    /** 运行来源：CHAT（用户消息）/ TRIGGER（定时触发器）。 */
    private String runSource;
    /** 定时触发器主键；用户消息路径为空。 */
    private Long triggerId;
    /** 定时触发器的单次触发记录主键；用户消息路径为空。 */
    private Long fireId;
    private ExecutionRunStatus status;
    private String currentStage;
    private Integer revision;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
