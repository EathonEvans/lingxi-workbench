package com.lingxi.workbench.application.runtime.common.enums;

/** 纯意图识别结果状态，不包含后续能力路由决策。 */
public enum IntentRecognitionStatus {
    /** 唯一候选满足阈值和分差要求。 */
    MATCHED,
    /** 多个候选分差不足，需要澄清。 */
    AMBIGUOUS,
    /** 没有候选达到召回阈值。 */
    NOT_MATCHED
}
