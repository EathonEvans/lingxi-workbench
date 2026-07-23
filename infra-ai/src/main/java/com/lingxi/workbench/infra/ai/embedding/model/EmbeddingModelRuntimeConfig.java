package com.lingxi.workbench.infra.ai.embedding.model;

/** 单次Embedding调用所需的运行时配置。 */
public record EmbeddingModelRuntimeConfig(
        Long instanceId,
        String baseUrl,
        String apiKey,
        String modelCode,
        int timeoutSeconds,
        int maxRetries) {
}
