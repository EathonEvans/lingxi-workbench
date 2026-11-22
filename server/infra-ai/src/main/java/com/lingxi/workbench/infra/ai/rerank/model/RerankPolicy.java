package com.lingxi.workbench.infra.ai.rerank.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 发布版本冻结的可选重排序策略，返回条数沿用检索 topK。
 * @param modelInstanceId 重排序模型实例
 * @param candidateTopK 向量召回候选上限
 * @param minScore 重排结果最低相关分数，与向量相似度阈值独立
 */
public record RerankPolicy(@NotNull @Positive Long modelInstanceId,
                           @NotNull @Min(1) @Max(100) Integer candidateTopK,
                           @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double minScore) {
}
