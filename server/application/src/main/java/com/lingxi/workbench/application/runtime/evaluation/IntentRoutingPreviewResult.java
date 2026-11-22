package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Application 草稿意图测试的只读结果，不产生审计、澄清会话或能力执行。 */
@Getter
@Builder
public class IntentRoutingPreviewResult {

    private final List<IntentRoutingPreviewCandidate> candidates;
    private final List<IntentRoutingPreviewCandidate> retrievedCandidates;
    private final IntentSemanticAction semanticAction;
    private final IntentDecisionReason semanticReasonCode;
    private final String selectedIntentCode;
    private final List<String> alternativeIntentCodes;
    private final List<IntentRecognizedSlot> recognizedSlots;
    private final IntentRoutingAction finalAction;
    private final IntentRoutingReason finalReasonCode;
    private final IntentExecutionTargetType targetType;
    private final String capabilityCode;
    private final Long modelInstanceId;
    private final Long promptVersionId;
    private final TokenUsage tokenUsage;
    private final int modelCalls;
    private final int retryCount;
    private final long durationMillis;
}
