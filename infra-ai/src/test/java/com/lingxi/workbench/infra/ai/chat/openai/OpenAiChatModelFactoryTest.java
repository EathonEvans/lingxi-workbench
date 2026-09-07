package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.openai.core.Timeout;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import io.micrometer.observation.ObservationRegistry;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenAiChatModelFactoryTest {

    @Test
    void shouldApplyDatabaseRuntimeOptionsToChatModel() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder()
                .instanceId(9L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("secret")
                .modelCode("deepseek-chat")
                .temperature(0.6)
                .topP(0.9)
                .maxTokens(2048)
                .stopSequences(List.of("END"))
                .extensionParameters(JSONObject.of(
                        "thinking",
                        JSONObject.of("type", "disabled")))
                .timeoutSeconds(45)
                .maxRetries(2)
                .build();

        OpenAiChatModel model = new OpenAiChatModelFactory(
                ObservationRegistry.NOOP, mock(ModelOutboundAddressPolicy.class)).create(config);
        OpenAiChatOptions options = model.getOptions();

        assertEquals("https://api.deepseek.com", options.getBaseUrl());
        assertEquals("deepseek-chat", options.getModel());
        assertEquals(0.6, options.getTemperature());
        assertEquals(0.9, options.getTopP());
        assertEquals(2048, options.getMaxTokens());
        assertEquals(List.of("END"), options.getStopSequences());
        assertEquals(
                JSONObject.of("type", "disabled"),
                options.getExtraBody().get("thinking"));
        assertEquals(Duration.ZERO, options.getTimeout());
        assertEquals(2, options.getMaxRetries());
        assertTrue(options.getStreamOptions().includeUsage());
        assertFalse(config.toString().contains("secret"));
        assertTrue(config.toString().contains("apiKey=******"));
    }

    @Test
    void shouldUseConfiguredTimeoutForConnectionAndResponseIdleOnly() {
        Timeout timeout = OpenAiChatModelFactory.responseIdleTimeout(Duration.ofSeconds(45));

        assertEquals(Duration.ofSeconds(45), timeout.connect());
        assertEquals(Duration.ofSeconds(45), timeout.read());
        assertEquals(Duration.ofSeconds(45), timeout.write());
        assertEquals(Duration.ZERO, timeout.request());
    }

    @Test
    void shouldReuseCachedClientForIdenticalConfig() {
        OpenAiChatModelFactory factory = new OpenAiChatModelFactory(
                ObservationRegistry.NOOP, mock(ModelOutboundAddressPolicy.class));
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder()
                .instanceId(9L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("secret")
                .modelCode("deepseek-chat")
                .timeoutSeconds(45)
                .maxRetries(2)
                .build();

        OpenAiChatModel first = factory.create(config);
        OpenAiChatModel second = factory.create(config);

        // 短 TTL 窗口内复用同一客户端，避免重复创建 HTTP 客户端与 TLS 连接
        assertTrue(first == second);
    }

    @Test
    void shouldRebuildClientWhenConfigChanges() {
        OpenAiChatModelFactory factory = new OpenAiChatModelFactory(
                ObservationRegistry.NOOP, mock(ModelOutboundAddressPolicy.class));
        ChatModelRuntimeConfig base = ChatModelRuntimeConfig.builder()
                .instanceId(9L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("old-key")
                .modelCode("deepseek-chat")
                .timeoutSeconds(45)
                .maxRetries(2)
                .build();
        ChatModelRuntimeConfig rotated = ChatModelRuntimeConfig.builder()
                .instanceId(9L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("new-key")
                .modelCode("deepseek-chat")
                .timeoutSeconds(45)
                .maxRetries(2)
                .build();

        OpenAiChatModel first = factory.create(base);
        OpenAiChatModel second = factory.create(rotated);

        // 密钥轮换后指纹变化，不再复用旧客户端
        assertTrue(first != second);
    }
}
