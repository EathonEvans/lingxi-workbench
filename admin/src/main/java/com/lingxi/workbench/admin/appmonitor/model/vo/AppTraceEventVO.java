package com.lingxi.workbench.admin.appmonitor.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 脱敏后的单条 Trace 事件。 */
@Getter
@Builder
public class AppTraceEventVO {
    private String spanId;
    private String parentSpanId;
    private String eventType;
    private String stage;
    private String status;
    private long latencyMs;
    private String modelCode;
    private String toolName;
    private String errorCode;
    private OffsetDateTime createdAt;
}
