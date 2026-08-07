package com.lingxi.workbench.application.runtime.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 计划步骤输入值的可信来源。 */
@Getter
@RequiredArgsConstructor
public enum StepInputSource {
    USER_QUERY("USER_QUERY", "用户原始请求"),
    LITERAL("LITERAL", "计划内常量"),
    STEP_OUTPUT("STEP_OUTPUT", "前置步骤输出");

    private final String code;
    private final String describe;
}
