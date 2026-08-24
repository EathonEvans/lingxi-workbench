package com.lingxi.workbench.infra.ai.chat.model.bo;

import com.alibaba.fastjson2.JSONObject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * 单次对话所需的最小模型运行配置。
 *
 * <p>API Key 只允许在 application 与 infra-ai 的内存调用链中短暂存在，禁止记录日志、
 * 写入缓存或通过接口返回。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatModelRuntimeConfig {
    /**
     * 模型实例主键，用于消息追溯和用量归属。
     */
    private Long instanceId;
    /**
     * OpenAI 兼容接口基础地址。
     */
    private String baseUrl;
    /**
     * 本次调用使用的明文 API Key，仅允许短暂存在于内存。
     */
    private String apiKey;
    /**
     * 供应商实际模型编码，例如具体的 Chat 模型名称。
     */
    private String modelCode;
    /**
     * 模型采样温度，为空时使用供应商默认值。
     */
    private Double temperature;
    /**
     * 核采样参数，为空时使用供应商默认值。
     */
    private Double topP;
    /**
     * 单次回答最大输出 Token 数，为空时使用供应商默认值。
     */
    private Integer maxTokens;
    /**
     * 命中任一内容时停止生成的序列集合。
     */
    private List<String> stopSequences;
    /**
     * 供应商特有的 OpenAI Chat 请求体扩展参数。
     *
     * <p>该字段仅在模型协议适配器边界转换为 {@code extraBody}，业务流程不解析其内容。</p>
     */
    private JSONObject extensionParameters;
    /** 连接、写入和连续无响应的空闲超时时间，单位为秒；持续响应不受总时长限制。 */
    private Integer timeoutSeconds;
    /**
     * 模型请求最大重试次数。
     */
    private Integer maxRetries;
    /** 模型实例已声明支持供应商原生 JSON Schema 结构化输出。 */
    private boolean structuredOutputSupported;

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
                + ", extensionParameterKeys="
                + Objects.requireNonNullElseGet(
                        extensionParameters, JSONObject::new).keySet()
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxRetries=" + maxRetries + "]";
    }
}
