package com.lingxi.workbench.application.runtime.evaluation;

/** 意图评测调用锁定 ApplicationVersion 决策链的稳定边界。 */
public interface IntentRoutingEvaluationPreview {

    IntentRoutingPreviewResult preview(Long applicationId, Long applicationVersionId, String query);
}
