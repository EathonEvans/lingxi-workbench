package com.lingxi.workbench.application.runtime.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选限定语义决策的模型动作，不直接代表最终能力执行。 */
@Getter
@RequiredArgsConstructor
public enum IntentSemanticAction {

    SELECT("SELECT", "选择单一候选"),
    CLARIFY("CLARIFY", "需要用户澄清"),
    DEFER("DEFER", "交回上层处理");

    private final String code;
    private final String describe;
}
