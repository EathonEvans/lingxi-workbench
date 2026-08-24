package com.lingxi.workbench.application.runtime.evaluation;

/** 基线样本覆盖的生产场景。 */
public enum IntentEvaluationScenario {
    STANDARD,
    NEGATION,
    REVISION,
    SIMILAR_INTENT,
    CONTEXT_FOLLOW_UP,
    MULTI_INTENT,
    OUT_OF_SCOPE,
    MISSING_SLOT,
    PROMPT_INJECTION
}
