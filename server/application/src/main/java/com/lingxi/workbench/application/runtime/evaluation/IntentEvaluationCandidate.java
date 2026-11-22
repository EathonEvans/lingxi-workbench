package com.lingxi.workbench.application.runtime.evaluation;

/**
 * 报表中的候选及其余弦相似度；该值不代表模型置信度。
 *
 * @param intentCode 意图编码
 * @param similarity 余弦相似度
 */
public record IntentEvaluationCandidate(String intentCode, double similarity) {
}
