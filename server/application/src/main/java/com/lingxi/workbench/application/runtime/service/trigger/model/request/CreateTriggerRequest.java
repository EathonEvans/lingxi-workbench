package com.lingxi.workbench.application.runtime.service.trigger.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 创建定时触发器请求；执行目标以“能力编码 + 能力类型”表达，运行端从冻结能力目录解析。 */
@Getter
@Setter
public class CreateTriggerRequest {

    /** 触发器名称。 */
    @NotBlank(message = "触发器名称不能为空")
    @Size(max = 64, message = "触发器名称不能超过64个字符")
    private String name;

    /** 绑定的 Application 主键。 */
    @NotNull(message = "应用不能为空")
    private Long applicationId;

    /** 绑定并冻结的 ApplicationVersion 主键。 */
    @NotNull(message = "应用版本不能为空")
    private Long applicationVersionId;

    /** 能力类型：AGENT / WORKFLOW。 */
    @NotBlank(message = "能力类型不能为空")
    @Pattern(regexp = "^(AGENT|WORKFLOW)$", message = "能力类型仅支持AGENT或WORKFLOW")
    private String capabilityType;

    /** 冻统能力目录中的能力编码。 */
    @NotBlank(message = "执行目标不能为空")
    @Size(max = 64, message = "能力编码不能超过64个字符")
    private String capabilityCode;

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

    /** 预授权工具码白名单；为空表示不预授权任何工具（只读语义）。 */
    private List<String> allowedToolCodes;
}
