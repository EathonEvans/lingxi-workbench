package com.lingxi.workbench.admin.appmonitor.model.bo;

import java.time.OffsetDateTime;

/**
 * 运行趋势聚合点。
 *
 * @param bucket      时间桶
 * @param total       运行数
 * @param successRuns 成功数
 */
public record AppTrendPointBO(OffsetDateTime bucket, long total, long successRuns) {
}
