package com.lingxi.workbench.application.runtime.evaluation;

/**
 * 草稿测试返回的安全候选摘要。
 *
 * @param intentCode 叶子意图编码
 * @param name       叶子意图名称
 * @param similarity 候选余弦相似度
 * @param rank       一基候选顺位
 */
public record IntentRoutingPreviewCandidate(String intentCode, String name, double similarity, int rank) {
}
