package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;

/** 根据数据库运行配置创建 OpenAI 兼容协议 ChatModel。 */
@Component
public class OpenAiCompatibleChatModelFactory {

    /**
     * 为单次对话创建模型客户端。
     *
     * <p>第一版不缓存客户端，确保管理端停用模型或轮换密钥后，新请求立即使用最新配置。</p>
     */
    public OpenAiChatModel create(ChatModelRuntimeConfig config) {
        // 连接地址、密钥、模型编码和容错参数都来自本次查询的运行配置，不使用进程启动时的固定配置。
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .model(config.getModelCode())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .maxRetries(config.getMaxRetries())
                // OpenAI 兼容协议通常在流式响应的最后一个数据块返回用量。
                .streamUsage(true);

        // 可选采样参数为空时不主动下发，让不同供应商使用各自默认值。
        if (Objects.nonNull(config.getTemperature())) {
            options.temperature(config.getTemperature());
        }
        if (Objects.nonNull(config.getTopP())) {
            options.topP(config.getTopP());
        }
        if (Objects.nonNull(config.getMaxTokens())) {
            options.maxTokens(config.getMaxTokens());
        }
        // 空停止序列不能下发，否则部分 OpenAI 兼容实现会拒绝请求。
        if (Objects.nonNull(config.getStopSequences()) && !config.getStopSequences().isEmpty()) {
            options.stop(config.getStopSequences());
        }
        // 扩展参数只在协议适配器边界转成 Map，避免供应商私有字段侵入领域模型。
        if (Objects.nonNull(config.getExtensionParameters())
                && !config.getExtensionParameters().isEmpty()) {
            options.extraBody(new LinkedHashMap<>(config.getExtensionParameters()));
        }

        // ChatModel 只服务当前调用，不缓存包含明文 API Key 的客户端对象。
        return OpenAiChatModel.builder()
                .options(options.build())
                .build();
    }
}
