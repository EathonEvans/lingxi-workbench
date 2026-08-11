package com.lingxi.workbench.admin.appmonitor.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** 应用监控总览响应。 */
@Getter
@Builder
public class AppMonitorOverviewVO {
    private AppMonitorSummaryVO summary;
    private List<AppHealthVO> applications;
    private List<AppStageMetricVO> stages;
    private List<AppTrendPointVO> trend;
    private List<AppIssueVO> issues;
    private OffsetDateTime refreshedAt;
}
