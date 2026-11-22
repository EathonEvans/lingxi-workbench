package com.lingxi.workbench.admin.evaluation.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 意图路由评测必须覆盖的业务场景。 */
@Getter
@RequiredArgsConstructor
public enum EvaluationScenario {

    POSITIVE("POSITIVE", "明确意图"),
    NEGATION("NEGATION", "否定表达"),
    CORRECTION("CORRECTION", "意图修正"),
    SIMILAR_INTENT("SIMILAR_INTENT", "相似意图区分"),
    FOLLOW_UP("FOLLOW_UP", "上下文追问"),
    MULTI_INTENT("MULTI_INTENT", "多意图"),
    OUT_OF_SCOPE("OUT_OF_SCOPE", "范围外问题"),
    MISSING_SLOT("MISSING_SLOT", "缺少必要信息"),
    PROMPT_INJECTION("PROMPT_INJECTION", "提示词注入");

    private final String code;
    private final String describe;
}
