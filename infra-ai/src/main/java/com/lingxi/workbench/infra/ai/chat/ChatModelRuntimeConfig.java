package com.lingxi.workbench.infra.ai.chat;

import java.util.List;

/**
 * 单次对话所需的最小模型运行配置。
 *
 * <p>API Key 只允许在 application 与 infra-ai 的内存调用链中短暂存在，禁止记录日志、
 * 写入缓存或通过接口返回。</p>
 */
public record ChatModelRuntimeConfig(
        Long instanceId,
        String baseUrl,
        String apiKey,
        String modelCode,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> stopSequences,
        Integer timeoutSeconds,
        Integer maxRetries) {

    /** 日志输出时强制隐藏 API Key，防止调用参数被整体打印后泄漏密钥。 */
    @Override
    public String toString() {
        return "ChatModelRuntimeConfig[instanceId=" + instanceId
                + ", baseUrl=" + baseUrl
                + ", apiKey=******"
                + ", modelCode=" + modelCode
                + ", temperature=" + temperature
                + ", topP=" + topP
                + ", maxTokens=" + maxTokens
                + ", stopSequences=" + stopSequences
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxRetries=" + maxRetries + "]";
    }
}
