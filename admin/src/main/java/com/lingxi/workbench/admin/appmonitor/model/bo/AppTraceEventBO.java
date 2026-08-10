package com.lingxi.workbench.admin.appmonitor.model.bo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 单条脱敏观测事件投影。 */
@Data
public class AppTraceEventBO {
    private String spanId;
    private String parentSpanId;
    private String eventType;
    private String stage;
    private String status;
    private Long latencyMs;
    private String modelCode;
    private String toolName;
    private OffsetDateTime createdAt;
}
