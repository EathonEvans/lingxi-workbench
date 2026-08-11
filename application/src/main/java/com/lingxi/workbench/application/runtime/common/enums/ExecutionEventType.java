package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可持久化并向用户展示的执行时间线事件。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionEventType {
    PLANNING("PLANNING", "正在规划"),
    PLAN_CREATED("PLAN_CREATED", "计划已生成"),
    STEP_STARTED("STEP_STARTED", "步骤已开始"),
    STEP_COMPLETED("STEP_COMPLETED", "步骤已完成"),
    STEP_FAILED("STEP_FAILED", "步骤失败"),
    REPLANNING("REPLANNING", "正在重新规划"),
    FINALIZING("FINALIZING", "正在整理现有结果"),
    CLARIFICATION_REQUIRED("CLARIFICATION_REQUIRED", "需要用户澄清"),
    RUN_COMPLETED("RUN_COMPLETED", "运行已完成"),
    RUN_FAILED("RUN_FAILED", "运行失败"),
    RUN_CANCELLED("RUN_CANCELLED", "运行已取消");

    @EnumValue
    private final String code;
    private final String describe;
}
