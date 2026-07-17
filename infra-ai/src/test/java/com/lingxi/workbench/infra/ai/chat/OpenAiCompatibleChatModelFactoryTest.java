package com.lingxi.workbench.infra.ai.chat;

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
        ChatModelRuntimeConfig config = new ChatModelRuntimeConfig(
                9L,
                "https://api.deepseek.com",
                "secret",
                "deepseek-chat",
                0.6,
                0.9,
                2048,
                List.of("END"),
                45,
                2);

        OpenAiChatModel model = new OpenAiCompatibleChatModelFactory().create(config);
        OpenAiChatOptions options = model.getOptions();

        assertEquals("https://api.deepseek.com", options.getBaseUrl());
        assertEquals("deepseek-chat", options.getModel());
        assertEquals(0.6, options.getTemperature());
        assertEquals(0.9, options.getTopP());
        assertEquals(2048, options.getMaxTokens());
        assertEquals(List.of("END"), options.getStopSequences());
        assertEquals(Duration.ofSeconds(45), options.getTimeout());
        assertEquals(2, options.getMaxRetries());
        assertTrue(options.getStreamOptions().includeUsage());
        assertFalse(config.toString().contains("secret"));
        assertTrue(config.toString().contains("apiKey=******"));
    }
}
