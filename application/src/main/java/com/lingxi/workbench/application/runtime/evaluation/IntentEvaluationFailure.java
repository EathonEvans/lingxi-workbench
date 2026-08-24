package com.lingxi.workbench.application.runtime.evaluation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 未达到期望的样本结果；报告只保留样本编码，不重复写入查询原文。 */
@Getter
public class IntentEvaluationFailure {

    private final String sampleId;
    private final IntentEvaluationExpectedAction expectedAction;
    private final IntentEvaluationExpectedAction actualAction;
    private final List<String> expectedIntentCodes;
    private final List<IntentEvaluationCandidate> candidates;

    @Builder
    private IntentEvaluationFailure(
            String sampleId,
            IntentEvaluationExpectedAction expectedAction,
            IntentEvaluationExpectedAction actualAction,
            List<String> expectedIntentCodes,
            List<IntentEvaluationCandidate> candidates) {
        this.sampleId = sampleId;
        this.expectedAction = expectedAction;
        this.actualAction = actualAction;
        this.expectedIntentCodes = expectedIntentCodes == null ? List.of() : List.copyOf(expectedIntentCodes);
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
