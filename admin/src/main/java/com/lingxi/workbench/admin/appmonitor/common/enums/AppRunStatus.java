package com.lingxi.workbench.admin.appmonitor.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 监控列表中的运行结果。 */
@Getter
@RequiredArgsConstructor
public enum AppRunStatus {
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String describe;

    public static AppRunStatus fromCode(String code) {
        for (AppRunStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("不支持的Application运行状态：" + code);
    }
}
