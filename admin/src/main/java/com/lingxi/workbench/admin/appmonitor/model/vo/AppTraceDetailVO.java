package com.lingxi.workbench.admin.appmonitor.model.vo;

import com.lingxi.workbench.admin.appmonitor.common.enums.TraceSourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Trace 脱敏详情及外部数据源状态。 */
@Getter
@Builder
public class AppTraceDetailVO {
    private String traceId;
    private String source;
    private TraceSourceStatus sourceStatus;
    private String sourceMessage;
    private AppTraceVO summary;
    private List<AppTraceEventVO> events;
}
