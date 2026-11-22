package com.lingxi.workbench.infra.ai.embedding.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次Embedding调用所需的运行时配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmbeddingModelRuntimeConfig {
    /**
     * 模型实例主键。
     */
    private Long instanceId;
    /**
     * Embedding 服务基础地址。
     */
    private String baseUrl;
    /**
     * 本次调用使用的明文 API Key，仅允许短暂存在于内存。
     */
    private String apiKey;
    /**
     * 供应商实际 Embedding 模型编码。
     */
    private String modelCode;
    /**
     * 请求超时时间（秒）。
     */
    private int timeoutSeconds;
    /**
     * 最大重试次数。
     */
    private int maxRetries;
}
