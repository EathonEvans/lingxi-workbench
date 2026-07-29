package com.lingxi.workbench.infra.ai.chat.openai;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleChatModelFactoryTest {

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

        OpenAiChatModel model = new OpenAiCompatibleChatModelFactory().create(config);
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
        assertEquals(Duration.ofSeconds(45), options.getTimeout());
        assertEquals(2, options.getMaxRetries());
        assertTrue(options.getStreamOptions().includeUsage());
        assertFalse(config.toString().contains("secret"));
        assertTrue(config.toString().contains("apiKey=******"));
    }
}
