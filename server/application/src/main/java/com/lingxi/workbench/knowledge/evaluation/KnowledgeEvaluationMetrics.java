package com.lingxi.workbench.knowledge.evaluation;

/**
 * 检索质量指标，以当前配置的 Top K 计算。
 * @param recallAtK 期望片段被命中的比例
 * @param mrr 第一个正确结果的排名倒数，未命中为零
 */
public record KnowledgeEvaluationMetrics(double recallAtK, double mrr) { }
