package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** Planner每次初始规划或重规划产生的不可变决策快照。 */
@Data
@TableName(value = "lingxi_core.ai_execution_plan_snapshot", autoResultMap = true)
public class ExecutionPlanSnapshotEntity {

    private Long id;
    private String runId;
    private Integer planNo;
    private ExecutionDecisionAction decisionAction;
    private String capabilityCode;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ExecutionDecision decisionSnapshot;
    private String snapshotChecksum;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
