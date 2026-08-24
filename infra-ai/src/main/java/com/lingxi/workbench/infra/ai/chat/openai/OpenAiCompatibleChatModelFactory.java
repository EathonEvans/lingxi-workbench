package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.openai.core.Timeout;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 根据数据库运行配置创建 OpenAI 兼容协议 ChatModel。 */
@Component
public class OpenAiCompatibleChatModelFactory {

    /**
     * 模型客户端短 TTL 缓存时长：管理端修改模型或轮换密钥后，最长 5 分钟生效，
     * 避免高频对话路径每次重建 HTTP 客户端与 TLS 连接。
     */
    private static final Duration CLIENT_CACHE_TTL = Duration.ofMinutes(5);

    private final ObservationRegistry observationRegistry;
    private final ModelOutboundAddressPolicy outboundAddressPolicy;
    private final ConcurrentHashMap<String, CachedModel> clientCache = new ConcurrentHashMap<>();

    public OpenAiCompatibleChatModelFactory(ObservationRegistry observationRegistry,
                                            ModelOutboundAddressPolicy outboundAddressPolicy) {
        this.observationRegistry = observationRegistry;
        this.outboundAddressPolicy = outboundAddressPolicy;
    }

    /**
     * 为单次对话创建（或复用）模型客户端。
     *
     * <p>按运行配置指纹短 TTL 缓存客户端：配置未变化时复用，避免重复握手；
     * TTL 过期后按最新配置重建，密钥轮换与停用最长 5 分钟生效。</p>
     */
    public OpenAiChatModel create(ChatModelRuntimeConfig config) {
        // 出站地址安全校验：配置即使绕过管理端保存校验被篡改，运行端连接前也会被拦截。
        outboundAddressPolicy.validateOutboundAddress(config.getBaseUrl());
        String fingerprint = fingerprint(config);
        CachedModel cached = clientCache.get(fingerprint);
        if (Objects.nonNull(cached) && !cached.isExpired()) {
            return cached.model();
        }
        // 并发下同一配置可能短暂重复创建，覆盖缓存即可，不影响正确性。
        OpenAiChatModel created = buildModel(config);
        clientCache.put(fingerprint, new CachedModel(created, Instant.now().plus(CLIENT_CACHE_TTL)));
        return created;
    }

    private OpenAiChatModel buildModel(ChatModelRuntimeConfig config) {
        Duration responseIdleTimeout = Duration.ofSeconds(config.getTimeoutSeconds());
        // 连接地址、密钥、模型编码和容错参数都来自本次查询的运行配置，不使用进程启动时的固定配置。
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .model(config.getModelCode())
                // 禁用整次HTTP调用倒计时；连接与连续无响应保护由底层OkHttp分别执行。
                .timeout(Duration.ZERO)
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
                .httpClientBuilderCustomizer(builder -> builder
                        .timeout(responseIdleTimeout(responseIdleTimeout))
                        .interceptor(chain -> chain
                                .withConnectTimeout(Math.toIntExact(responseIdleTimeout.toMillis()), TimeUnit.MILLISECONDS)
                                .withReadTimeout(Math.toIntExact(responseIdleTimeout.toMillis()), TimeUnit.MILLISECONDS)
                                .withWriteTimeout(Math.toIntExact(responseIdleTimeout.toMillis()), TimeUnit.MILLISECONDS)
                                .proceed(chain.request())))
                // 必须显式使用应用级 ObservationRegistry，否则手动创建的 ChatModel 只会上报 NOOP 观测。
                .observationRegistry(observationRegistry)
                .build();
    }

    /** 连接、写入和连续无响应有边界，但持续返回数据的调用没有总时限。 */
    static Timeout responseIdleTimeout(Duration timeout) {
        return Timeout.builder()
                .connect(timeout)
                .read(timeout)
                .write(timeout)
                .request(Duration.ZERO)
                .build();
    }

    /**
     * 运行配置指纹：影响模型行为的全部字段参与哈希。
     * 使用 SHA-256 摘要作为缓存键，避免明文 API Key 出现在缓存键或日志中。
     */
    private static String fingerprint(ChatModelRuntimeConfig config) {
        String source = String.join("|",
                Objects.toString(config.getBaseUrl(), ""),
                Objects.toString(config.getApiKey(), ""),
                Objects.toString(config.getModelCode(), ""),
                Objects.toString(config.getTemperature(), ""),
                Objects.toString(config.getTopP(), ""),
                Objects.toString(config.getMaxTokens(), ""),
                Objects.toString(config.getStopSequences(), ""),
                String.valueOf(config.getTimeoutSeconds()),
                String.valueOf(config.getMaxRetries()),
                Objects.toString(config.getExtensionParameters(), ""));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256算法", exception);
        }
    }

    /** 带过期时间的缓存项。 */
    private record CachedModel(OpenAiChatModel model, Instant expiresAt) {

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
