package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.TriggerStatus;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/** 定时触发器实体。 */
@Getter
@Setter
@TableName(value = "lingxi_core.ai_trigger", autoResultMap = true)
public class TriggerEntity extends TimeEntity {

    private Long id;

    /** 归属用户主键。 */
    private Long userId;

    /** 绑定的 Application 主键。 */
    private Long applicationId;

    /** 绑定并冻结的 ApplicationVersion 主键，重新发布不自动跟进。 */
    private Long applicationVersionId;

    /** 执行模式：AGENT / WORKFLOW。 */
    private String executionMode;

    /** AGENT 模式绑定的 Agent 主键。 */
    private Long agentId;

    /** AGENT 模式绑定的 AgentVersion 主键。 */
    private Long agentVersionId;

    /** WORKFLOW 模式绑定的 Workflow 主键。 */
    private Long workflowId;

    /** WORKFLOW 模式绑定的 WorkflowVersion 主键。 */
    private Long workflowVersionId;

    /** 专属会话主键，主动运行在该会话中产出消息。 */
    private Long conversationId;

    /** 触发器名称。 */
    private String name;

    /** 目标模板，支持 {{now}} 有限变量，渲染后作为执行问题。 */
    private String goalTemplate;

    /** Spring 6 段 cron 表达式。 */
    private String cronExpr;

    /** cron 求值时区。 */
    private String timezone;

    /** 静默时段 HH:mm-HH:mm，落点在窗口内时顺延到窗口结束。 */
    private String quietHours;

    /** 预授权工具码白名单；为空表示不预授权任何工具。 */
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> allowedToolCodes;

    /** 触发器状态。 */
    @EnumValue
    private TriggerStatus status;

    /** 下次到期时间，扫描与抢占的唯一调度依据。 */
    private OffsetDateTime nextFireAt;

    /** 最近一次运行的主键。 */
    private String lastRunId;

    /** 最近一次运行的终态编码。 */
    private String lastRunStatus;

    /** 最近一次运行完成时间。 */
    private OffsetDateTime lastRunAt;

    /** 连续失败次数，成功后清零，达到阈值自动暂停。 */
    private Integer consecutiveFailures;

    /** 自动暂停原因。 */
    private String pauseReason;

    /** 软删除时间。 */
    private OffsetDateTime deletedAt;
}
