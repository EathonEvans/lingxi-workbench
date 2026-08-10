package com.lingxi.workbench.admin.appmonitor.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trace 详情数据源状态。 */
@Getter
@RequiredArgsConstructor
public enum TraceSourceStatus {
    AVAILABLE("AVAILABLE", "Langfuse数据可用"),
    DISABLED("DISABLED", "Langfuse查询未启用"),
    EMPTY("EMPTY", "Langfuse暂未返回数据"),
    UNAVAILABLE("UNAVAILABLE", "Langfuse查询不可用");

    private final String code;
    private final String describe;
}
