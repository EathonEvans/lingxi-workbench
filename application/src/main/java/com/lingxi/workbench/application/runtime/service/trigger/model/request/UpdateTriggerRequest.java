package com.lingxi.workbench.application.runtime.service.trigger.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 更新定时触发器请求；执行目标不可变更，需变更时重新创建。 */
@Getter
@Setter
public class UpdateTriggerRequest {

    /** 触发器名称。 */
    @NotBlank(message = "触发器名称不能为空")
    @Size(max = 64, message = "触发器名称不能超过64个字符")
    private String name;

    /** 目标模板，支持 {{now}} 变量。 */
    @NotBlank(message = "任务目标不能为空")
    @Size(max = 2000, message = "任务目标不能超过2000个字符")
    private String goalTemplate;

    /** Spring 6 段 cron 表达式。 */
    @NotBlank(message = "调度表达式不能为空")
    @Size(max = 32, message = "调度表达式不能超过32个字符")
    private String cronExpr;

    /** cron 求值时区。 */
    private String timezone;

    /** 静默时段 HH:mm-HH:mm，可空。 */
    private String quietHours;

    /** 预授权工具码白名单；为空表示不预授权任何工具。 */
    private List<String> allowedToolCodes;
}
