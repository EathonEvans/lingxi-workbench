package com.lingxi.workbench.admin.appmonitor.model.bo;

/**
 * 单个阶段聚合指标。
 *
 * @param stage      阶段编码
 * @param total      执行数
 * @param errorCount 失败数
 * @param p95Ms      P95耗时
 */
public record AppStageMetricBO(String stage, long total, long errorCount, long p95Ms) {
}
