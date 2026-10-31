package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 触发器单次到期触发记录，是自治运行的审计与对账依据。 */
@Getter
@Setter
@TableName("lingxi_core.ai_trigger_fire")
public class TriggerFireEntity {

    /** 记录主键。 */
    private Long id;

    /** 归属触发器主键。 */
    private Long triggerId;

    /** 归属用户主键。 */
    private Long userId;

    /** 计划触发时间（cron 求值结果）。 */
    private OffsetDateTime plannedAt;

    /** 实际触发时间。 */
    private OffsetDateTime firedAt;

    /** 关联的执行运行主键。 */
    private String runId;

    /** 触发生命周期状态。 */
    @EnumValue
    private TriggerFireStatus status;

    /** 跳过原因；仅 SKIPPED 状态有值。 */
    private String skipReason;

    /** 失败摘要。 */
    private String errorMessage;

    /** 记录创建时间，由填充器维护。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
