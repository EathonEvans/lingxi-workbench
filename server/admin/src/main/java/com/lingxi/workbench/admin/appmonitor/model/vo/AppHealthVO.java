package com.lingxi.workbench.admin.appmonitor.model.vo;

import com.lingxi.workbench.admin.appmonitor.common.enums.AppHealthStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 应用健康状态响应。 */
@Getter
@Builder
public class AppHealthVO {
    private String applicationId;
    private String applicationName;
    private AppHealthStatus status;
    private long totalRuns;
    private long failureRuns;
    private double successRate;
    private long p95Ms;
    private OffsetDateTime lastRunAt;
}
