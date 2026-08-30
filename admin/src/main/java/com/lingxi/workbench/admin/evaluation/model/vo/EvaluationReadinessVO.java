package com.lingxi.workbench.admin.evaluation.model.vo;

/**
 * @param ready       是否满足当前生产门禁
 * @param sampleCount 当前数据集样本数
 * @param message     同源门禁校验结果
 */
public record EvaluationReadinessVO(boolean ready, int sampleCount, String message) {
}
