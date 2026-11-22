package com.lingxi.workbench.application.runtime.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 已完成逐条决策后的评测报告生成请求。
 *
 * @param dataset 锁定版本和数据集快照
 * @param results 与样本一一对应的决策结果
 */
public record IntentRoutingEvaluationReportRequest(
        @NotNull @Valid IntentRoutingEvaluationDataset dataset,
        @NotNull @Size(max = 1000) List<@Valid IntentRoutingEvaluatedSample> results) {
}
