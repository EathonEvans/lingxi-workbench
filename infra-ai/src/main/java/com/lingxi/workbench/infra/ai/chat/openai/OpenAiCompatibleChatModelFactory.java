package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .model(config.modelCode())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .maxRetries(config.maxRetries())
                // OpenAI 兼容协议通常在流式响应的最后一个数据块返回用量。
                .streamUsage(true);

        // 可选采样参数为空时不主动下发，让不同供应商使用各自默认值。
        if (Objects.nonNull(config.temperature())) {
            options.temperature(config.temperature());
        }
        if (Objects.nonNull(config.topP())) {
            options.topP(config.topP());
        }
        if (Objects.nonNull(config.maxTokens())) {
            options.maxTokens(config.maxTokens());
        }
        // 空停止序列不能下发，否则部分 OpenAI 兼容实现会拒绝请求。
        if (Objects.nonNull(config.stopSequences()) && !config.stopSequences().isEmpty()) {
            options.stop(config.stopSequences());
        }

        // ChatModel 只服务当前调用，不缓存包含明文 API Key 的客户端对象。
        return OpenAiChatModel.builder()
                .options(options.build())
                .build();
    }
}
