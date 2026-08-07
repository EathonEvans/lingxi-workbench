package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Planner 允许返回的顶层执行动作。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionDecisionAction {
    EXECUTE_REACT("EXECUTE_REACT", "执行ReAct Agent"),
    EXECUTE_WORKFLOW("EXECUTE_WORKFLOW", "执行Workflow"),
    EXECUTE_PLAN("EXECUTE_PLAN", "执行计划"),
    CLARIFY("CLARIFY", "请求用户澄清");

    @EnumValue
    private final String code;
    private final String describe;
}
