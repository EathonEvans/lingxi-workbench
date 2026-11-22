package com.lingxi.workbench.application.runtime.evaluation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 绑定到不可变发布版本的离线评测数据集。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentEvaluationDataset {

    /** 数据集协议版本，V1 基线固定为 {@value #SCHEMA_VERSION}。 */
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion;
    private String datasetVersion;
    private Long applicationId;
    private Long applicationVersionId;
    private Long intentTreeId;
    private Long intentTreeVersionId;
    private Long embeddingModelInstanceId;
    private List<IntentEvaluationLeafMetadata> leafMetadata;
    private List<IntentEvaluationSample> samples;

    public List<IntentEvaluationLeafMetadata> leafMetadata() {
        return leafMetadata == null ? List.of() : List.copyOf(leafMetadata);
    }

    public List<IntentEvaluationSample> samples() {
        return samples == null ? List.of() : List.copyOf(samples);
    }
}
