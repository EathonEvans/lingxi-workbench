package com.lingxi.workbench.admin.appmonitor.model.bo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 最近一次 Application 运行的数据库投影。 */
@Data
public class AppTraceBO {
    private String traceId;
    private Long applicationId;
    private String applicationName;
    private String executionMode;
    private String status;
    private Long latencyMs;
    private Long modelCalls;
    private Long toolCalls;
    private Long totalTokens;
    private OffsetDateTime startedAt;
}
