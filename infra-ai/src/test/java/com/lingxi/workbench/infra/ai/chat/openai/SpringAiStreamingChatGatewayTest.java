package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiStreamingChatGatewayTest {

    @Test
    void shouldUseRuntimeModelAndConvertNonEmptyChunks() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(4);
        when(usage.getTotalTokens()).thenReturn(14);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("你"), response(""), response("好"), response("", usage)));
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        SpringAiStreamingChatGateway gateway =
                new SpringAiStreamingChatGateway(factory);

        List<ChatStreamChunk> chunks = gateway.stream(new ChatStreamCommand(
                        "你好",
                        List.of(
                                new ChatHistoryMessage(ChatHistoryMessage.Role.USER, "上一问"),
                                new ChatHistoryMessage(ChatHistoryMessage.Role.ASSISTANT, "上一答")),
                        config))
                .collectList()
                .block();

        assertEquals(List.of(
                new ChatStreamChunk("你"),
                new ChatStreamChunk("好"),
                new ChatStreamChunk("", 10, 4, 14)), chunks);
        verify(factory).create(config);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        List<?> messages = promptCaptor.getValue().getInstructions();
        assertEquals(4, messages.size());
        assertEquals("上一问", ((UserMessage) messages.get(1)).getText());
        assertEquals("上一答", ((AssistantMessage) messages.get(2)).getText());
        assertEquals("你好", ((UserMessage) messages.get(3)).getText());
    }

    private static ChatModelRuntimeConfig config() {
        return ChatModelRuntimeConfig.builder()
                .instanceId(1L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("secret")
                .modelCode("deepseek-chat")
                .temperature(0.7)
                .maxTokens(1024)
                .stopSequences(List.of())
                .timeoutSeconds(60)
                .maxRetries(1)
                .build();
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static ChatResponse response(String content, Usage usage) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))), metadata);
    }
}
