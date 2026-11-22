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
