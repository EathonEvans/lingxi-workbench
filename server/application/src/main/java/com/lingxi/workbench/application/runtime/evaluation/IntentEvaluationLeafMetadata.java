package com.lingxi.workbench.application.runtime.evaluation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 已发布叶子意图及其 Application 映射快照，用于样本编写时人工校对。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentEvaluationLeafMetadata {

    private String intentCode;
    private String name;
    private String description;
    private List<String> positiveExamples;
    private List<String> negativeExamples;
    private String targetType;
    private String capabilityCode;
    private Long targetId;
    private Long targetVersionId;

    public List<String> positiveExamples() {
        return positiveExamples == null ? List.of() : List.copyOf(positiveExamples);
    }

    public List<String> negativeExamples() {
        return negativeExamples == null ? List.of() : List.copyOf(negativeExamples);
    }
}
