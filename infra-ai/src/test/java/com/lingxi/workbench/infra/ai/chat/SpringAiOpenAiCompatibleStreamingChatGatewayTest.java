package com.lingxi.workbench.infra.ai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiOpenAiCompatibleStreamingChatGatewayTest {

    @Test
    void shouldUseRuntimeModelAndConvertNonEmptyChunks() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("你"), response(""), response("好")));
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        var gateway = new SpringAiOpenAiCompatibleStreamingChatGateway(factory);

        List<ChatStreamChunk> chunks = gateway.stream(new ChatStreamCommand("你好", config))
                .collectList()
                .block();

        assertEquals(List.of(new ChatStreamChunk("你"), new ChatStreamChunk("好")), chunks);
        verify(factory).create(config);
        verify(chatModel).stream(any(Prompt.class));
    }

    private static ChatModelRuntimeConfig config() {
        return new ChatModelRuntimeConfig(
                1L, "https://api.deepseek.com", "secret", "deepseek-chat",
                0.7, null, 1024, List.of(), 60, 1);
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
