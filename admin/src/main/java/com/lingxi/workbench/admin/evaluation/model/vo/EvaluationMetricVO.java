package com.lingxi.workbench.admin.evaluation.model.vo;

/**
 * 管理端评测维度聚合。
 *
 * @param metric       评测维度枚举名
 * @param label        中文名称
 * @param count        评分数量
 * @param averageScore 平均分
 */
public record EvaluationMetricVO(String metric, String label, long count, double averageScore) {
}
