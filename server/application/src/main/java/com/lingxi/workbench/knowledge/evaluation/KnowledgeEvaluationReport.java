package com.lingxi.workbench.knowledge.evaluation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 向量 Top K 与最终 Top K 的同次召回对照，权限/空结果断言独立呈现。 */
@Getter
@Builder
public class KnowledgeEvaluationReport {
    private final String datasetVersion;
    private final boolean passed;
    private final List<KnowledgeEvaluatedSample> samples;
    /** 没有正例时为 null，避免把未评测误报为零分。 */
    private final KnowledgeEvaluationMetrics vectorMetrics;
    private final KnowledgeEvaluationMetrics finalMetrics;
}
