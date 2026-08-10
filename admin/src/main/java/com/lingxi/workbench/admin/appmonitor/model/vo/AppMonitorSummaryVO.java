package com.lingxi.workbench.admin.appmonitor.model.vo;

/**
 * 应用监控核心指标。
 *
 * @param totalRuns     运行总数
 * @param successRate   成功率
 * @param avgLatencyMs  平均耗时
 * @param totalTokens   Token总量
 */
public record AppMonitorSummaryVO(long totalRuns, double successRate, long avgLatencyMs, long totalTokens) {
}
