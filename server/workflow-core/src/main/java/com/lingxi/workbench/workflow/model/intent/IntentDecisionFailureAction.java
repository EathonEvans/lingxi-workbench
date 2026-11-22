package com.lingxi.workbench.workflow.model.intent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 意图语义决策达到失败上限后的显式动作。 */
@Getter
@RequiredArgsConstructor
public enum IntentDecisionFailureAction {

    DEFER("DEFER", "转交Fallback Agent"),
    FAIL("FAIL", "终止本次请求");

    private final String code;

    private final String describe;
}
