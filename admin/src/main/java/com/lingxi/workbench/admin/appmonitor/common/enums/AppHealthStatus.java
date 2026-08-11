package com.lingxi.workbench.admin.appmonitor.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 应用在当前统计窗口内的运行健康状态。 */
@Getter
@RequiredArgsConstructor
public enum AppHealthStatus {
    HEALTHY("HEALTHY", "健康"),
    DEGRADED("DEGRADED", "需关注"),
    CRITICAL("CRITICAL", "严重"),
    NO_DATA("NO_DATA", "暂无数据");

    private final String code;
    private final String describe;
}
