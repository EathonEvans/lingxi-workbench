package com.lingxi.workbench.workflow.model.intent;

import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 意图树节点定义；执行目标由 Application 单独映射，不进入该对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentNodeDefinition {

    private String id;
    private String code;
    private String parentId;
    private String name;
    private String description;
    private IntentNodeType nodeType;
    private List<String> positiveExamples;
    private List<String> negativeExamples;
    private List<IntentSlotDefinition> slots;
    private String clarificationQuestion;
    private Integer priority;
    private boolean enabled;
}
