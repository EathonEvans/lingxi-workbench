package com.lingxi.workbench.application.runtime.service.trigger.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 触发器单次运行历史响应对象。 */
@Getter
@Setter
public class TriggerFireVO {

    /** 触发记录主键（字符串形式）。 */
    private String id;

    /** 触发器主键（字符串形式）。 */
    private String triggerId;

    /** 计划触发时间。 */
    private OffsetDateTime plannedAt;

    /** 实际触发时间。 */
    private OffsetDateTime firedAt;

    /** 触发状态编码：FIRED / RUNNING / SUCCEEDED / FAILED / CANCELLED / SKIPPED / EXPIRED。 */
    private String status;

    /** 跳过原因编码。 */
    private String skipReason;

    /** 失败摘要。 */
    private String errorMessage;
}
