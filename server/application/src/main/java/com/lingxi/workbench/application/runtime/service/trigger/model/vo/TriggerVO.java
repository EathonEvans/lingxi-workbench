package com.lingxi.workbench.application.runtime.service.trigger.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/** 定时触发器响应对象。 */
@Getter
@Setter
public class TriggerVO {

    /** 触发器主键（字符串形式，避免前端精度丢失）。 */
    private String id;

    /** 触发器名称。 */
    private String name;

    /** 触发器状态编码：ACTIVE / PAUSED / ARCHIVED。 */
    private String status;

    /** 执行模式：AGENT / WORKFLOW。 */
    private String executionMode;

    /** 绑定的能力名称（来自冻结能力目录）。 */
    private String capabilityName;

    /** 专属会话主键。 */
    private String conversationId;

    /** 目标模板。 */
    private String goalTemplate;

    /** cron 表达式。 */
    private String cronExpr;

    /** 求值时区。 */
    private String timezone;

    /** 静默时段。 */
    private String quietHours;

    /** 预授权工具码白名单。 */
    private List<String> allowedToolCodes;

    /** 下次触发时间。 */
    private OffsetDateTime nextFireAt;

    /** 最近一次运行状态。 */
    private String lastRunStatus;

    /** 最近一次运行时间。 */
    private OffsetDateTime lastRunAt;

    /** 连续失败次数。 */
    private Integer consecutiveFailures;

    /** 暂停原因。 */
    private String pauseReason;

    /** 创建时间。 */
    private OffsetDateTime createdAt;

    /** 更新时间。 */
    private OffsetDateTime updatedAt;
}
