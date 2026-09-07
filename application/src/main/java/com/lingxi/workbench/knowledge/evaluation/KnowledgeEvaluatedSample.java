package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchDiagnostics;
import lombok.Builder;
import lombok.Getter;

/** 样本报告不复制查询、正文或供应商异常；失败样本保留明确错误码。 */
@Getter
@Builder
public class KnowledgeEvaluatedSample {
    private final String id;
    private final boolean passed;
    private final Integer errorCode;
    private final KnowledgeEvaluationMetrics vectorMetrics;
    private final KnowledgeEvaluationMetrics finalMetrics;
    private final KnowledgeSearchDiagnostics diagnostics;
}
