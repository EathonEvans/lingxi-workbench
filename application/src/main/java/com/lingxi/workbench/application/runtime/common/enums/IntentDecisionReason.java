package com.lingxi.workbench.application.runtime.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 意图语义决策使用的稳定原因编码，禁止保存自由解释文本。 */
@Getter
@RequiredArgsConstructor
public enum IntentDecisionReason {

    SINGLE_INTENT("SINGLE_INTENT", "单一候选语义明确"),
    AMBIGUOUS_INTENT("AMBIGUOUS_INTENT", "多个候选语义接近"),
    MISSING_REQUIRED_SLOT("MISSING_REQUIRED_SLOT", "目标明确但缺少必填槽位"),
    OUT_OF_SCOPE("OUT_OF_SCOPE", "请求不属于候选范围"),
    MULTIPLE_INTENTS("MULTIPLE_INTENTS", "请求包含多个独立意图"),
    NEGATED_OR_CORRECTED("NEGATED_OR_CORRECTED", "否定或改口后无法安全收束"),
    INSUFFICIENT_CONTEXT("INSUFFICIENT_CONTEXT", "上下文不足"),
    NO_SUITABLE_CANDIDATE("NO_SUITABLE_CANDIDATE", "候选均不适合"),
    MODEL_BUDGET_EXHAUSTED("MODEL_BUDGET_EXHAUSTED", "Application模型调用或Token工作预算已用尽"),
    SEMANTIC_DECISION_FAILED("SEMANTIC_DECISION_FAILED", "语义决策调用或校验失败");

    private final String code;
    private final String describe;
}
