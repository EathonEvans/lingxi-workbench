package com.lingxi.workbench.application.runtime.evaluation;

import lombok.Builder;
import lombok.Getter;

/** V1 基线报告汇总指标。 */
@Getter
@Builder
public class IntentEvaluationMetrics {

    private final double candidateRecallAtK;
    private final double top1Accuracy;
    private final double ambiguityRate;
    private final double rejectionRate;
    private final double finalActionAccuracy;
}
