package com.lingxi.workbench.workflow.model.intent;

import java.math.BigDecimal;

/**
 * 意图树候选召回的默认策略。
 *
 * @param topK 候选叶子数量
 * @param recallThreshold 最低召回相似度
 * @param ambiguityMargin Top-1 与 Top-2 的最小安全分差
 */
public record IntentRoutingPolicy(
        int topK,
        BigDecimal recallThreshold,
        BigDecimal ambiguityMargin) {
}
