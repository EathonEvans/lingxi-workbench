package com.lingxi.workbench.application.runtime.evaluation;

/**
 * @param code        稳定门禁编码
 * @param value       实际指标值
 * @param requirement 人工可读的门禁要求
 * @param passed      是否通过
 */
public record IntentRoutingEvaluationGate(String code, double value, String requirement, boolean passed) {
}
