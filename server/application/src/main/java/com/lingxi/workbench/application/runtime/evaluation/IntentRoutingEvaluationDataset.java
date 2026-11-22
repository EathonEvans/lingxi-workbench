package com.lingxi.workbench.application.runtime.evaluation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** 锁定不可变 ApplicationVersion、IntentTreeVersion、模型和Prompt的 V2 评测数据集。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingEvaluationDataset {

    public static final int SCHEMA_VERSION = 2;

    private int schemaVersion;
    private String datasetVersion;
    private String labelingOwner;
    private Instant labeledAt;
    private String sourceDescription;
    private Long applicationId;
    private Long applicationVersionId;
    private Long intentTreeId;
    private Long intentTreeVersionId;
    private Long embeddingModelInstanceId;
    private Long chatModelInstanceId;
    private Long promptVersionId;
    private List<IntentEvaluationLeafMetadata> leafMetadata;
    private List<IntentRoutingEvaluationSample> samples;

    public List<IntentEvaluationLeafMetadata> leafMetadata() {
        return leafMetadata == null ? List.of() : List.copyOf(leafMetadata);
    }

    public List<IntentRoutingEvaluationSample> samples() {
        return samples == null ? List.of() : List.copyOf(samples);
    }
}
