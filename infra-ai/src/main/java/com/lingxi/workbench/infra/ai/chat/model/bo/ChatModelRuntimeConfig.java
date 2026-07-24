package com.lingxi.workbench.infra.ai.chat.model.bo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单次对话所需的最小模型运行配置。
 *
 * <p>API Key 只允许在 application 与 infra-ai 的内存调用链中短暂存在，禁止记录日志、
 * 写入缓存或通过接口返回。</p>
 *
 * @param instanceId    模型实例主键，用于消息追溯和用量归属。
 * @param baseUrl       OpenAI 兼容接口基础地址。
 * @param apiKey        本次调用使用的明文 API Key，仅允许短暂存在于内存。
 * @param modelCode     供应商实际模型编码，例如具体的 Chat 模型名称。
 * @param temperature   模型采样温度，为空时使用供应商默认值。
 * @param topP          核采样参数，为空时使用供应商默认值。
 * @param maxTokens     单次回答最大输出 Token 数，为空时使用供应商默认值。
 * @param stopSequences 命中任一内容时停止生成的序列集合。
 * @param timeoutSeconds 单次模型请求超时时间，单位为秒。
 * @param maxRetries    模型请求最大重试次数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatModelRuntimeConfig {
    /** 模型实例主键，用于消息追溯和用量归属。 */
    private Long instanceId;
    /** OpenAI 兼容接口基础地址。 */
    private String baseUrl;
    /** 本次调用使用的明文 API Key，仅允许短暂存在于内存。 */
    private String apiKey;
    /** 供应商实际模型编码，例如具体的 Chat 模型名称。 */
    private String modelCode;
    /** 模型采样温度，为空时使用供应商默认值。 */
    private Double temperature;
    /** 核采样参数，为空时使用供应商默认值。 */
    private Double topP;
    /** 单次回答最大输出 Token 数，为空时使用供应商默认值。 */
    private Integer maxTokens;
    /** 命中任一内容时停止生成的序列集合。 */
    private List<String> stopSequences;
    /** 单次模型请求超时时间，单位为秒。 */
    private Integer timeoutSeconds;
    /** 模型请求最大重试次数。 */
    private Integer maxRetries;

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
