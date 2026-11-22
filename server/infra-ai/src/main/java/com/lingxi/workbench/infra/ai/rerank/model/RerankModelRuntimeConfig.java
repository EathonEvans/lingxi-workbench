package com.lingxi.workbench.infra.ai.rerank.model;

import lombok.Builder;
import lombok.Getter;

/** 重排序连接配置，仅在内存传递密钥，不写入发布快照或日志。 */
@Getter
@Builder(toBuilder = true)
public class RerankModelRuntimeConfig {
    private final Long instanceId;
    private final String baseUrl;
    private final String apiKey;
    private final String modelCode;
    private final Integer timeoutSeconds;
    private final Integer maxRetries;
}
