package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 单个计划步骤的一次追加式执行尝试。 */
@Data
@TableName(value = "lingxi_core.ai_execution_step_attempt", autoResultMap = true)
public class ExecutionStepAttemptEntity {

    private Long id;
    private String runId;
    private Integer planNo;
    private String stepId;
    private Integer attemptNo;
    private String capabilityCode;
    private ExecutionStepStatus status;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private PlanStep stepSnapshot;
    private String resultContent;
    private String errorCode;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
