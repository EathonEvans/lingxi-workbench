package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;

/**
 * @param expectedAction 期望最终动作
 * @param actualAction   实际最终动作
 * @param count          样本数
 */
public record IntentRoutingEvaluationConfusionEntry(
        IntentRoutingAction expectedAction, IntentRoutingAction actualAction, long count) {
}
