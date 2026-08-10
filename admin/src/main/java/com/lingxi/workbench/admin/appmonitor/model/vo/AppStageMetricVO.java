package com.lingxi.workbench.admin.appmonitor.model.vo;

import lombok.Builder;
import lombok.Getter;

/** 应用执行阶段健康指标。 */
@Getter
@Builder
public class AppStageMetricVO {
    private String stage;
    private String name;
    private long total;
    private long errorCount;
    private double successRate;
    private long p95Ms;
}
