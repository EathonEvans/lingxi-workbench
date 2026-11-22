package com.lingxi.workbench.infra.ai.embedding;

import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;

import java.util.List;

/**
 * 文本向量化网关。
 */
public interface EmbeddingGateway {
    /**
     * 按输入顺序返回向量，返回数量必须与输入数量一致。
     */
    List<float[]> embed(List<String> texts, EmbeddingModelRuntimeConfig config);
}
