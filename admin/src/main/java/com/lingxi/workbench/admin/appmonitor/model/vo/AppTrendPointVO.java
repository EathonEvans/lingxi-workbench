package com.lingxi.workbench.admin.appmonitor.model.vo;

import java.time.OffsetDateTime;

/**
 * 运行趋势点。
 *
 * @param bucket      时间桶
 * @param total       运行数
 * @param successRuns 成功数
 */
public record AppTrendPointVO(OffsetDateTime bucket, long total, long successRuns) {
}
