package com.lingxi.workbench.admin.appmonitor.model.bo;

import com.lingxi.workbench.admin.appmonitor.common.enums.TraceSourceStatus;

import java.util.List;

/**
 * Langfuse Trace 查询结果。
 *
 * @param status       数据源状态
 * @param message      可展示的状态说明
 * @param observations 脱敏 observation 列表
 */
public record LangfuseTraceResult(TraceSourceStatus status, String message, List<LangfuseObservationBO> observations) {
    public LangfuseTraceResult {
        observations = List.copyOf(observations);
    }
}
