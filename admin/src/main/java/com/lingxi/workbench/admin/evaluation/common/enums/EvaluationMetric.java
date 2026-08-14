package com.lingxi.workbench.admin.evaluation.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/** 平台统一的人工评测维度，Langfuse Score 名称由平台固定维护。 */
@Getter
@RequiredArgsConstructor
public enum EvaluationMetric {

    CORRECTNESS("lingxi.correctness", "准确性"),
    RELEVANCE("lingxi.relevance", "相关性"),
    COMPLETENESS("lingxi.completeness", "完整性"),
    SAFETY("lingxi.safety", "安全性"),
    OVERALL("lingxi.overall", "综合质量");

    private final String scoreName;
    private final String label;

    public static Optional<EvaluationMetric> fromScoreName(String scoreName) {
        return Arrays.stream(values()).filter(metric -> metric.scoreName.equals(scoreName)).findFirst();
    }
}
