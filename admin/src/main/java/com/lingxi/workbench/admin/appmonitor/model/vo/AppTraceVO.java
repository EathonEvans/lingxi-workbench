package com.lingxi.workbench.admin.appmonitor.model.vo;

import com.lingxi.workbench.admin.appmonitor.common.enums.AppExecutionMode;
import com.lingxi.workbench.admin.appmonitor.common.enums.AppRunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 应用运行链路摘要。 */
@Getter
@Builder
public class AppTraceVO {
    private String traceId;
    private String applicationId;
    private String applicationName;
    private AppExecutionMode executionMode;
    private AppRunStatus status;
    private long latencyMs;
    private long modelCalls;
    private long toolCalls;
    private long totalTokens;
    private OffsetDateTime startedAt;
}
