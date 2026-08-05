package com.lingxi.workbench.workflow.model.intent;

import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 叶子意图需要从用户请求中提取的结构化槽位。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentSlotDefinition {

    private String code;
    private String name;
    private String description;
    private IntentSlotValueType valueType;
    private boolean required;
}
