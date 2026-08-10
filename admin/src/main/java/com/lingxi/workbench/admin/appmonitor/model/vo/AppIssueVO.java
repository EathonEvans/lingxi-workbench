package com.lingxi.workbench.admin.appmonitor.model.vo;

import java.time.OffsetDateTime;

/**
 * 异常聚合项。
 *
 * @param errorType 异常类型
 * @param stage     阶段
 * @param count     数量
 * @param latestAt  最近发生时间
 */
public record AppIssueVO(String errorType, String stage, long count, OffsetDateTime latestAt) {
}
