package com.lingxi.workbench.application.runtime.evaluation;

import org.springframework.stereotype.Component;

/** Application 服务内执行评测时直接复用生产版本预览链路，避免自调用 HTTP。 */
@Component
public class LocalIntentRoutingEvaluationPreview implements IntentRoutingEvaluationPreview {

    private final IntentRoutingPreviewService previewService;

    public LocalIntentRoutingEvaluationPreview(IntentRoutingPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    public IntentRoutingPreviewResult preview(Long applicationId, Long applicationVersionId, String query) {
        return previewService.previewVersion(new IntentRoutingVersionPreviewRequest(
                applicationId, applicationVersionId, query));
    }
}
