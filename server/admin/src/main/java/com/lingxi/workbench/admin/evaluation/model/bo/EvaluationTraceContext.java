package com.lingxi.workbench.admin.evaluation.model.bo;

/**
 * 已通过本地业务校验的评测 Trace 上下文。
 *
 * @param traceId         Trace ID
 * @param applicationId   Application 主键
 * @param applicationName Application 名称
 */
public record EvaluationTraceContext(String traceId, Long applicationId, String applicationName) {
}
