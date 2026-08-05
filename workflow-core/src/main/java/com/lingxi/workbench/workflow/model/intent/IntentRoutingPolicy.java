package com.lingxi.workbench.workflow.model.intent;

import java.math.BigDecimal;

/**
 * 意图树候选召回的默认策略参数。
 *
 * <p>这些参数定义在 IntentTree 级别，Application 绑定意图树时继承该策略。
 * 候选 LLM 复判阶段（M3）可在此基础上追加置信度门控。</p>
 *
 * <p>回顾流程：</p>
 * <ol>
 *   <li>用户输入向量化后与意图树版本内所有叶子向量做余弦相似度计算</li>
 *   <li>按相似度降序取 Top-K 个候选</li>
 *   <li>过滤掉低于召回阈值的候选</li>
 *   <li>如果 Top-1 与 Top-2 的相似度差值小于歧义分差，触发 INTENT_DISAMBIGUATION 澄清</li>
 * </ol>
 *
 * @param topK             候选叶子数量上限
 * @param recallThreshold  最低召回相似度，低于此值的候选被丢弃
 * @param ambiguityMargin  Top-1 与 Top-2 的最小安全分差，低于此值视为歧义，
 *                         需要用户从候选中明确选择
 */
public record IntentRoutingPolicy(
        int topK,
        BigDecimal recallThreshold,
        BigDecimal ambiguityMargin) {
}