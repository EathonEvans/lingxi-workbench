package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 根据已发布运行配置创建 AgentScope OpenAI 兼容模型。 */
@Component
public class AgentScopeOpenAiChatModelFactory {

    private final ModelOutboundAddressPolicy outboundAddressPolicy;

    public AgentScopeOpenAiChatModelFactory(ModelOutboundAddressPolicy outboundAddressPolicy) {
        this.outboundAddressPolicy = outboundAddressPolicy;
    }

    /**
     * 为一次 AgentScope 调用创建专属模型资源。
     *
     * <p>模型客户端不跨调用缓存，确保模型地址、密钥和发布配置每次均以当前锁定快照生效；资源流结束时
     * 必须关闭，不能让旧密钥驻留在连接池中。</p>
     */
    public AgentScopeChatModelResource create(ChatModelRuntimeConfig config) {
        Objects.requireNonNull(config, "模型运行配置不能为空");
        // 即使配置绕过控制面校验被篡改，实际连接前仍必须执行出站 SSRF 校验。
        outboundAddressPolicy.validateOutboundAddress(config.getBaseUrl());
        OkHttpTransport transport = new OkHttpTransport(buildTransportConfig(config));
        try {
            OpenAIChatModel model = OpenAIChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModelCode())
                    .stream(true)
                    .generateOptions(buildGenerateOptions(config))
                    .httpTransport(transport)
                    .nativeStructuredOutput(config.isStructuredOutputSupported())
                    .build();
            return new AgentScopeChatModelResource(model, transport);
        } catch (RuntimeException exception) {
            transport.close();
            throw exception;
        }
    }

    static HttpTransportConfig buildTransportConfig(ChatModelRuntimeConfig config) {
        Duration responseIdleTimeout = Duration.ofSeconds(config.getTimeoutSeconds());
        // OkHttp 的 read timeout 是连续无响应边界，不是整次请求的总时限。
        return HttpTransportConfig.builder()
                .connectTimeout(responseIdleTimeout)
                .readTimeout(responseIdleTimeout)
                .writeTimeout(responseIdleTimeout)
                .build();
    }

    static GenerateOptions buildGenerateOptions(ChatModelRuntimeConfig config) {
        GenerateOptions.Builder options = GenerateOptions.builder()
                .stream(true)
                // AgentScope 的 maxAttempts 包含首次调用；Lingxi 配置表达的是额外重试次数。
                .executionConfig(ExecutionConfig.builder()
                        .maxAttempts(Math.addExact(config.getMaxRetries(), 1))
                        .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
                        .build());
        if (Objects.nonNull(config.getTemperature())) {
            options.temperature(config.getTemperature());
        }
        if (Objects.nonNull(config.getTopP())) {
            options.topP(config.getTopP());
        }
        if (Objects.nonNull(config.getMaxTokens())) {
            options.maxTokens(config.getMaxTokens());
        }

        Map<String, Object> additionalBodyParameters = new LinkedHashMap<>();
        if (Objects.nonNull(config.getExtensionParameters()) && !config.getExtensionParameters().isEmpty()) {
            additionalBodyParameters.putAll(new LinkedHashMap<>(config.getExtensionParameters()));
        }
        if (Objects.nonNull(config.getStopSequences()) && !config.getStopSequences().isEmpty()) {
            // 已发布的标准停止序列优先于供应商扩展字段，防止扩展参数绕过版本约束。
            additionalBodyParameters.put("stop", List.copyOf(config.getStopSequences()));
        }
        if (!additionalBodyParameters.isEmpty()) {
            options.additionalBodyParams(additionalBodyParameters);
        }
        return options.build();
    }
}
