package com.lingxi.workbench.admin.evaluation.model.bo;

/**
 * Langfuse 数值评分聚合结果。
 *
 * @param name         Score 名称
 * @param total        评分数量
 * @param passing      不低于四分的评分数量
 * @param averageValue 平均分
 */
public record LangfuseMetricBO(String name, long total, long passing, double averageValue) {
}
