package com.lingxi.workbench.infra.ai.chat.openai;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransportConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentScopeOpenAiChatModelFactoryTest {

    @Test
    void shouldProjectPublishedRuntimeConfigToAgentScopeOptions() {
        ChatModelRuntimeConfig config = config();

        GenerateOptions options = AgentScopeOpenAiChatModelFactory.buildGenerateOptions(config);
        HttpTransportConfig transportConfig = AgentScopeOpenAiChatModelFactory.buildTransportConfig(config);

        assertTrue(options.getStream());
        assertEquals(0.6, options.getTemperature());
        assertEquals(0.9, options.getTopP());
        assertEquals(2048, options.getMaxTokens());
        assertEquals(3, options.getExecutionConfig().getMaxAttempts());
        assertEquals(List.of("END"), options.getAdditionalBodyParams().get("stop"));
        assertEquals(JSONObject.of("type", "disabled"), options.getAdditionalBodyParams().get("thinking"));
        assertEquals(Duration.ofSeconds(45), transportConfig.getConnectTimeout());
        assertEquals(Duration.ofSeconds(45), transportConfig.getReadTimeout());
        assertEquals(Duration.ofSeconds(45), transportConfig.getWriteTimeout());
    }

    @Test
    void shouldValidateOutboundAddressBeforeCreatingModelResource() {
        ModelOutboundAddressPolicy policy = mock(ModelOutboundAddressPolicy.class);
        AgentScopeOpenAiChatModelFactory factory = new AgentScopeOpenAiChatModelFactory(policy);

        try (AgentScopeChatModelResource resource = factory.create(config())) {
            assertEquals("deepseek-chat", resource.model().getModelName());
        }

        verify(policy).validateOutboundAddress("https://api.deepseek.com");
    }

    private static ChatModelRuntimeConfig config() {
        return ChatModelRuntimeConfig.builder()
                .instanceId(9L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("secret")
                .modelCode("deepseek-chat")
                .temperature(0.6)
                .topP(0.9)
                .maxTokens(2048)
                .stopSequences(List.of("END"))
                .extensionParameters(JSONObject.of("thinking", JSONObject.of("type", "disabled")))
                .timeoutSeconds(45)
                .maxRetries(2)
                .build();
    }
}
