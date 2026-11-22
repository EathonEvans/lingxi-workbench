package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.Builder;
import lombok.Getter;

/** 一次意图语义决策及其完整模型调用元数据。 */
@Getter
@Builder
public class IntentSemanticDecisionResult {

    private final IntentSemanticDecision decision;
    private final TokenUsage tokenUsage;
    private final int modelCalls;
    private final int retryCount;
    private final Long modelInstanceId;
    private final Long promptVersionId;
    private final long durationMillis;
}
