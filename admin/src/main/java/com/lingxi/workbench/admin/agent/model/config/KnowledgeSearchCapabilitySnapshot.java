package com.lingxi.workbench.admin.agent.model.config;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private BigDecimal similarityThreshold;
    private Integer maxResultTokens;
    private Integer maxQueryCharacters;
    private Integer timeoutSeconds;
}
