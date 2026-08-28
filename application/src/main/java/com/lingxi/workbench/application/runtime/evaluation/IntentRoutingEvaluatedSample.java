package com.lingxi.workbench.application.runtime.evaluation;

/**
 * 一条已经完成生产路由决策的评测结果。
 *
 * @param sampleId 样本标识
 * @param result   生产版本决策结果
 */
public record IntentRoutingEvaluatedSample(String sampleId, IntentRoutingPreviewResult result) {
}
