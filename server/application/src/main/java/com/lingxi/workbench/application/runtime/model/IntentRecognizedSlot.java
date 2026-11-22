package com.lingxi.workbench.application.runtime.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型提出的槽位编码和值，仍需服务端按候选定义重新校验。 */
@Data
@NoArgsConstructor
public class IntentRecognizedSlot {

    @JsonProperty(required = true)
    private String slotCode;
    @JsonProperty(required = true)
    private IntentSemanticSlotValue value;
}
