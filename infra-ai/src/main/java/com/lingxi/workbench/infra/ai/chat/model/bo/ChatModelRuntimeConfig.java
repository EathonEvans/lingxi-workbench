package com.lingxi.workbench.infra.ai.chat.model.bo;

import java.util.List;

/**
 * 单次对话所需的最小模型运行配置。
 *
 * <p>API Key 只允许在 application 与 infra-ai 的内存调用链中短暂存在，禁止记录日志、
 * 写入缓存或通过接口返回。</p>
 */
public record ChatModelRuntimeConfig(
        /** 模型实例主键，用于消息追溯和用量归属。 */
        Long instanceId,
        /** OpenAI 兼容接口基础地址。 */
        String baseUrl,
        /** 本次调用使用的明文 API Key，仅允许短暂存在于内存。 */
        String apiKey,
        /** 供应商实际模型编码，例如具体的 Chat 模型名称。 */
        String modelCode,
        /** 模型采样温度，为空时使用供应商默认值。 */
        Double temperature,
        /** 核采样参数，为空时使用供应商默认值。 */
        Double topP,
        /** 单次回答最大输出 Token 数，为空时使用供应商默认值。 */
        Integer maxTokens,
        /** 命中任一内容时停止生成的序列集合。 */
        List<String> stopSequences,
        /** 单次模型请求超时时间，单位为秒。 */
        Integer timeoutSeconds,
        /** 模型请求最大重试次数。 */
        Integer maxRetries) {

    /**
     * 日志输出时强制隐藏 API Key，防止调用参数被整体打印后泄漏密钥。
     */
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
