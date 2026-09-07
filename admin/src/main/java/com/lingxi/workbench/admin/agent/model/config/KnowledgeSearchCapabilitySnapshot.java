package com.lingxi.workbench.admin.agent.model.config;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.lingxi.workbench.infra.ai.rerank.model.RerankPolicy;
import jakarta.validation.Valid;
import java.math.BigDecimal;

/** ReAct Agent发布时冻结的知识搜索Tool权限与预算。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeSearchCapabilitySnapshot {
    private Long knowledgeBaseId;
    private Long knowledgeIndexVersionId;
    private Long embeddingModelInstanceId;
    private Integer topK;
    /** 可选的重排序策略；为空时仅使用向量排序。 */
    @Valid
    private RerankPolicy rerank;
    private BigDecimal similarityThreshold;
    private Integer maxResultTokens;
    private Integer maxQueryCharacters;
    private Integer timeoutSeconds;
}
