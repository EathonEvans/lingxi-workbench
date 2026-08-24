package com.lingxi.workbench.application.runtime.evaluation;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** 可保存和版本对比的 V2 只读评测报告。 */
@Getter
@Builder
public class IntentRoutingEvaluationReport {

    private final int schemaVersion;
    private final String datasetVersion;
    private final Instant generatedAt;
    private final Long applicationId;
    private final Long applicationVersionId;
    private final Long intentTreeId;
    private final Long intentTreeVersionId;
    private final Long embeddingModelInstanceId;
    private final Long chatModelInstanceId;
    private final Long promptVersionId;
    private final int sampleCount;
    private final boolean productionGatePassed;
    private final IntentRoutingEvaluationMetrics metrics;
    private final List<IntentRoutingEvaluationGate> gates;
    private final List<IntentRoutingEvaluationConfusionEntry> confusionMatrix;
    private final List<IntentRoutingEvaluationFailure> failures;
}
