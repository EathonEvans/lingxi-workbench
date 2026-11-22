package com.lingxi.workbench.application.runtime.evaluation;

/** V1 离线评测样本期望的最终路由动作。 */
public enum IntentEvaluationExpectedAction {
    /** 期望唯一命中一个叶子意图。 */
    MATCHED,
    /** 期望保留多个候选并进入澄清。 */
    AMBIGUOUS,
    /** 期望没有候选通过 V1 召回阈值。 */
    REJECTED
}
