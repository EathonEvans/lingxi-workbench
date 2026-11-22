package com.lingxi.workbench.admin.appmonitor.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Application 的稳定执行模式。 */
@Getter
@RequiredArgsConstructor
public enum AppExecutionMode {
    REACT("REACT", "ReAct"),
    PLAN_EXECUTE("PLAN_EXECUTE", "Plan & Execute"),
    WORKFLOW("WORKFLOW", "Workflow");

    private final String code;
    private final String describe;

    public static AppExecutionMode fromCode(String code) {
        for (AppExecutionMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("不支持的Application执行模式：" + code);
    }
}
