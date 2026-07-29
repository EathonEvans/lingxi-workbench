package com.lingxi.workbench.knowledge.service;

import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;

import java.util.List;

/**
 * 知识库语义检索内部服务，供对话链路调用。
 */
public interface KnowledgeSearchService {

    /**
     * 使用 AgentVersion 冻结的检索契约，在指定知识库中召回相关 Chunk。
     *
     * @param query               检索问题
     * @param knowledgeBaseId     AgentVersion 绑定的知识库主键
     * @param embeddingConfig     AgentVersion 绑定的 Embedding 模型运行配置
     * @param topK                AgentVersion 配置的最大召回条数
     * @param similarityThreshold AgentVersion 配置的最低相似度
     * @return 按相似度降序排列的检索结果
     */
    List<KnowledgeSearchResultVO> search(
            String query,
            Long knowledgeBaseId,
            EmbeddingModelRuntimeConfig embeddingConfig,
            int topK,
            double similarityThreshold);
}
