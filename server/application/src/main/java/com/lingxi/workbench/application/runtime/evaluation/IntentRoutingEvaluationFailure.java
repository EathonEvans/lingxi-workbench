package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** V2 失败样本下钻；用 sampleId 回到受控数据集查看原文。 */
@Getter
@Builder
public class IntentRoutingEvaluationFailure {

    private final String sampleId;
    private final IntentEvaluationScenario scenario;
    private final IntentRoutingAction expectedAction;
    private final IntentRoutingAction actualAction;
    private final List<String> expectedIntentCodes;
    private final String selectedIntentCode;
    private final List<IntentRoutingPreviewCandidate> candidates;
    private final String semanticReasonCode;
    private final String finalReasonCode;
}
