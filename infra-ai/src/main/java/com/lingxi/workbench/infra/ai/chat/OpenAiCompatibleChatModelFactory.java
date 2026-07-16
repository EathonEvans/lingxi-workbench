package com.lingxi.workbench.infra.ai.chat;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 根据数据库运行配置创建 OpenAI 兼容协议 ChatModel。 */
@Component
public class OpenAiCompatibleChatModelFactory {

    /**
     * 为单次对话创建模型客户端。
     *
     * <p>第一版不缓存客户端，确保管理端停用模型或轮换密钥后，新请求立即使用最新配置。</p>
     */
    public OpenAiChatModel create(ChatModelRuntimeConfig config) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .model(config.modelCode())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .maxRetries(config.maxRetries());

        if (config.temperature() != null) {
            options.temperature(config.temperature());
        }
        if (config.topP() != null) {
            options.topP(config.topP());
        }
        if (config.maxTokens() != null) {
            options.maxTokens(config.maxTokens());
        }
        if (config.stopSequences() != null && !config.stopSequences().isEmpty()) {
            options.stop(config.stopSequences());
        }

        return OpenAiChatModel.builder()
                .options(options.build())
                .build();
    }
}
