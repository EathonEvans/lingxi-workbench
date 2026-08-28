package com.lingxi.workbench.admin.evaluation.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 意图路由评测允许人工标注的最终动作。 */
@Getter
@RequiredArgsConstructor
public enum EvaluationExpectedAction {

    EXECUTE_AGENT("EXECUTE_AGENT", "执行智能能力"),
    EXECUTE_WORKFLOW("EXECUTE_WORKFLOW", "执行工作流"),
    FIXED_RESPONSE("FIXED_RESPONSE", "返回固定答复"),
    CLARIFICATION("CLARIFICATION", "要求补充信息"),
    DEFER_TO_AGENT("DEFER_TO_AGENT", "交由主助手处理");

    private final String code;
    private final String describe;
}
