package com.lingxi.workbench.admin.appmonitor.model.bo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 应用健康聚合查询结果。 */
@Data
public class AppHealthBO {
    private Long applicationId;
    private String applicationName;
    private long totalRuns;
    private long successRuns;
    private long failureRuns;
    private long p95Ms;
    private OffsetDateTime lastRunAt;
}
