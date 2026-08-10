package com.lingxi.workbench.admin.appmonitor.model.bo;

/**
 * 监控核心聚合指标。
 *
 * @param totalRuns   运行总数
 * @param successRuns 成功运行数
 * @param avgLatencyMs 平均耗时
 * @param totalTokens Token总量
 */
public record AppMonitorSummaryBO(long totalRuns, long successRuns, long avgLatencyMs, long totalTokens) {
}
