package com.lingxi.workbench.application.runtime.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 提供给后续语义决策的安全意图候选，不包含目标主键、权限或 Tool 参数。
 */
@Getter
public class IntentCandidate {

    private final String intentCode;
    private final String name;
    private final List<String> parentPath;
    private final String description;
    private final List<String> positiveExamples;
    private final List<String> negativeExamples;
    private final List<IntentCandidateSlot> slots;
    private final String clarificationQuestion;
    private final double similarity;
    private final int rank;

    @Builder
    private IntentCandidate(
            String intentCode,
            String name,
            List<String> parentPath,
            String description,
            List<String> positiveExamples,
            List<String> negativeExamples,
            List<IntentCandidateSlot> slots,
            String clarificationQuestion,
            double similarity,
            int rank) {
        this.intentCode = intentCode;
        this.name = name;
        this.parentPath = parentPath == null ? List.of() : List.copyOf(parentPath);
        this.description = description;
        this.positiveExamples = positiveExamples == null ? List.of() : List.copyOf(positiveExamples);
        this.negativeExamples = negativeExamples == null ? List.of() : List.copyOf(negativeExamples);
        this.slots = slots == null ? List.of() : List.copyOf(slots);
        this.clarificationQuestion = clarificationQuestion;
        this.similarity = similarity;
        this.rank = rank;
    }

    public String intentCode() {
        return intentCode;
    }

    public double similarity() {
        return similarity;
    }
}
