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
    private Long modelInstanceId;
    private String modelInstanceCode;
    private String modelCode;
    private String modelProviderName;
    private Integer modelCallNo;
    private Integer promptTokens;
    private Integer completionTokens;
    private String toolName;
    private Long firstResponseMs;
    private String errorCode;
    private OffsetDateTime createdAt;
}
