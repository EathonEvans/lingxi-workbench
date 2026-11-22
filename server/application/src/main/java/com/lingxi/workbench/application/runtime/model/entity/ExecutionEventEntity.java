package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import lombok.Data;

import java.time.OffsetDateTime;

/** 不含模型推理和工具原始参数的持久化执行时间线事件。 */
@Data
@TableName("lingxi_core.ai_execution_event")
public class ExecutionEventEntity {

    private Long id;
    private String runId;
    private Long sequenceNo;
    private ExecutionEventType eventType;
    private Integer planNo;
    private String stepId;
    private String displayName;
    private ExecutionStepStatus stepStatus;
    private String message;
    private OffsetDateTime occurredAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
