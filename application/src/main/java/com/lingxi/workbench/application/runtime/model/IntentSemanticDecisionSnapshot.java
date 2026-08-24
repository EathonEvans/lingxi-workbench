package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/** 模型语义决策的安全快照，只保留结构化编码，不持久化槽位值或推理文本。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentSemanticDecisionSnapshot {

    private final IntentSemanticAction action;
    private final IntentDecisionReason reason;
    private final String selectedIntentCode;
    private final List<String> alternativeIntentCodes;
    private final List<String> recognizedSlotCodes;

    public List<String> alternativeIntentCodes() {
        return List.copyOf(Objects.requireNonNullElse(alternativeIntentCodes, List.of()));
    }

    public List<String> recognizedSlotCodes() {
        return List.copyOf(Objects.requireNonNullElse(recognizedSlotCodes, List.of()));
    }
}
