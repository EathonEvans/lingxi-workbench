package com.lingxi.workbench.application.runtime.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 由平台稳定 JSON Schema 约束并经严格解析得到的候选限定意图决策。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentSemanticDecision {

    @JsonProperty(required = true)
    private IntentSemanticAction action;
    @JsonProperty(required = false)
    private String selectedIntentCode;
    @JsonProperty(required = false)
    private List<String> alternativeIntentCodes;
    @JsonProperty(required = false)
    private List<IntentRecognizedSlot> recognizedSlots;
    @JsonProperty(required = true)
    private IntentDecisionReason reasonCode;

    public List<String> alternativeIntentCodes() {
        return alternativeIntentCodes == null ? List.of() : List.copyOf(alternativeIntentCodes);
    }

    public List<IntentRecognizedSlot> recognizedSlots() {
        return recognizedSlots == null ? List.of() : List.copyOf(recognizedSlots);
    }
}
