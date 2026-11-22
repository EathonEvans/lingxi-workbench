package com.lingxi.workbench.admin.appmonitor.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trace 详情数据源状态。 */
@Getter
@RequiredArgsConstructor
public enum TraceSourceStatus {
    AVAILABLE("AVAILABLE", "完整链路数据可用"),
    DISABLED("DISABLED", "完整链路查询未启用"),
    EMPTY("EMPTY", "完整链路暂未返回数据"),
    UNAVAILABLE("UNAVAILABLE", "完整链路查询不可用");

    private final String code;
    private final String describe;
}
