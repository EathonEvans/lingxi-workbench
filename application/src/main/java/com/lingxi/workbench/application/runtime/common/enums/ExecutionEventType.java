package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可持久化并向用户展示的执行时间线事件。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionEventType {
    RUN_STARTED("RUN_STARTED", "运行已开始"),
    AGENT_STARTED("AGENT_STARTED", "开始处理"),
    AGENT_COMPLETED("AGENT_COMPLETED", "处理完成"),
    AGENT_PARTIAL("AGENT_PARTIAL", "部分完成"),
    AGENT_FAILED("AGENT_FAILED", "处理失败"),
    AGENT_CANCELLED("AGENT_CANCELLED", "处理已停止"),
    SKILL_ACTIVATED("SKILL_ACTIVATED", "已准备专业能力"),
    MODEL_STARTED("MODEL_STARTED", "正在分析"),
    MODEL_COMPLETED("MODEL_COMPLETED", "分析完成"),
    TOOL_STARTED("TOOL_STARTED", "操作已开始"),
    TOOL_REUSED("TOOL_REUSED", "已有结果已复用"),
    TOOL_COMPLETED("TOOL_COMPLETED", "操作已完成"),
    TOOL_FAILED("TOOL_FAILED", "操作失败"),
    COORDINATION_STARTED("COORDINATION_STARTED", "协作阶段已开始"),
    COORDINATION_COMPLETED("COORDINATION_COMPLETED", "协作阶段已完成"),
    COORDINATION_FAILED("COORDINATION_FAILED", "协作阶段失败"),
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
    RUN_RECONCILIATION_PENDING("RUN_RECONCILIATION_PENDING", "操作结果等待确认"),
    RUN_FAILED("RUN_FAILED", "运行失败"),
    RUN_CANCELLED("RUN_CANCELLED", "运行已取消");

    @EnumValue
    private final String code;
    private final String describe;
}
