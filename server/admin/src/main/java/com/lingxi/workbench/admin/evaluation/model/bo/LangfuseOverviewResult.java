package com.lingxi.workbench.admin.evaluation.model.bo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;

import java.util.List;

/**
 * Langfuse 评测概览查询结果。
 *
 * @param status       数据源状态
 * @param message      可展示的状态说明
 * @param metrics      各评分维度聚合
 * @param datasetCount 数据集数量
 */
public record LangfuseOverviewResult(EvaluationSourceStatus status, String message,
                                     List<LangfuseMetricBO> metrics, long datasetCount) {
}
