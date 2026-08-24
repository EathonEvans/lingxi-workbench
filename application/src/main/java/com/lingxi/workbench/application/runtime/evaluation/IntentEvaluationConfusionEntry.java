package com.lingxi.workbench.application.runtime.evaluation;

/**
 * 混淆矩阵中的一个期望动作、实际动作组合。
 *
 * @param expectedAction 期望动作
 * @param actualAction   实际动作
 * @param count          样本数
 */
public record IntentEvaluationConfusionEntry(
        IntentEvaluationExpectedAction expectedAction,
        IntentEvaluationExpectedAction actualAction,
        long count) {
}
