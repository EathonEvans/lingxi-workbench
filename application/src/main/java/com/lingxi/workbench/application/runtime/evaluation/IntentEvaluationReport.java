package com.lingxi.workbench.application.runtime.evaluation;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** 指定发布版本和数据集的一次只读离线评测报告。 */
@Getter
public class IntentEvaluationReport {

    private final int schemaVersion;
    private final String datasetVersion;
    private final Instant generatedAt;
    private final Long applicationId;
    private final Long applicationVersionId;
    private final Long intentTreeId;
    private final Long intentTreeVersionId;
    private final Long embeddingModelInstanceId;
    private final int sampleCount;
    private final IntentEvaluationMetrics metrics;
    private final List<IntentEvaluationConfusionEntry> confusionMatrix;
    private final List<IntentEvaluationFailure> failures;

    @Builder
    private IntentEvaluationReport(
            int schemaVersion,
            String datasetVersion,
            Instant generatedAt,
            Long applicationId,
            Long applicationVersionId,
            Long intentTreeId,
            Long intentTreeVersionId,
            Long embeddingModelInstanceId,
            int sampleCount,
            IntentEvaluationMetrics metrics,
            List<IntentEvaluationConfusionEntry> confusionMatrix,
            List<IntentEvaluationFailure> failures) {
        this.schemaVersion = schemaVersion;
        this.datasetVersion = datasetVersion;
        this.generatedAt = generatedAt;
        this.applicationId = applicationId;
        this.applicationVersionId = applicationVersionId;
        this.intentTreeId = intentTreeId;
        this.intentTreeVersionId = intentTreeVersionId;
        this.embeddingModelInstanceId = embeddingModelInstanceId;
        this.sampleCount = sampleCount;
        this.metrics = metrics;
        this.confusionMatrix = confusionMatrix == null ? List.of() : List.copyOf(confusionMatrix);
        this.failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
