package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可持久化并向用户展示的执行时间线事件。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionEventType {
    RUN_STARTED("RUN_STARTED", "运行已开始"),
    AGENT_STARTED("AGENT_STARTED", "Agent已开始"),
    AGENT_COMPLETED("AGENT_COMPLETED", "Agent已完成"),
    AGENT_PARTIAL("AGENT_PARTIAL", "Agent部分完成"),
    AGENT_FAILED("AGENT_FAILED", "Agent调用失败"),
    AGENT_CANCELLED("AGENT_CANCELLED", "Agent调用已取消"),
    SKILL_ACTIVATED("SKILL_ACTIVATED", "Skill已激活"),
    MODEL_STARTED("MODEL_STARTED", "模型调用已开始"),
    MODEL_COMPLETED("MODEL_COMPLETED", "模型调用已完成"),
    TOOL_STARTED("TOOL_STARTED", "工具调用已开始"),
    TOOL_REUSED("TOOL_REUSED", "工具结果已复用"),
    TOOL_COMPLETED("TOOL_COMPLETED", "工具调用已完成"),
    TOOL_FAILED("TOOL_FAILED", "工具调用失败"),
    HUMAN_REQUIRED("HUMAN_REQUIRED", "等待人工确认"),
    HUMAN_RESOLVED("HUMAN_RESOLVED", "人工确认已生效"),
    PLANNING("PLANNING", "正在规划"),
    PLAN_CREATED("PLAN_CREATED", "计划已生成"),
    STEP_STARTED("STEP_STARTED", "步骤已开始"),
    STEP_REUSED("STEP_REUSED", "步骤结果已复用"),
    STEP_COMPLETED("STEP_COMPLETED", "步骤已完成"),
    STEP_FAILED("STEP_FAILED", "步骤失败"),
    REPLANNING("REPLANNING", "正在重新规划"),
    FINALIZING("FINALIZING", "正在整理现有结果"),
    CLARIFICATION_REQUIRED("CLARIFICATION_REQUIRED", "需要用户澄清"),
    RUN_COMPLETED("RUN_COMPLETED", "运行已完成"),
    RUN_PARTIAL("RUN_PARTIAL", "运行已部分完成"),
    RUN_RECONCILIATION_PENDING("RUN_RECONCILIATION_PENDING", "工具结果等待对账"),
    RUN_FAILED("RUN_FAILED", "运行失败"),
    RUN_CANCELLED("RUN_CANCELLED", "运行已取消");

    @EnumValue
    private final String code;
    private final String describe;
}
