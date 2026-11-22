package com.lingxi.workbench.application.runtime.evaluation;

import lombok.Builder;
import lombok.Getter;

/** V2 最终路由、成本和延迟指标。 */
@Getter
@Builder
public class IntentRoutingEvaluationMetrics {

    private final double candidateRecallAtK;
    private final double finalActionAccuracy;
    private final double selectedIntentAccuracy;
    private final double executablePrecision;
    private final double outOfScopeDeferRate;
    private final double multiIntentSafeRate;
    private final double highRiskWrongDirectRate;
    private final double semanticFailureRate;
    private final long p95DurationMillis;
    private final double averageTotalTokens;
    private final double averageModelCalls;
}
