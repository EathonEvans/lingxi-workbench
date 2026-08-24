package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import io.micrometer.observation.ObservationRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiChatCompletionGatewayTest {

    @Test
    void shouldKeepGenericCompletionAsPlainText() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatResponse response = response();
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder()
                .model("planner-model")
                .maxTokens(512)
                .build());
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        SpringAiChatClientFactory chatClientFactory = new SpringAiChatClientFactory(ObservationRegistry.NOOP);
        SpringAiChatCompletionGateway gateway = new SpringAiChatCompletionGateway(
                factory, chatClientFactory, ObservationRegistry.NOOP);
        ChatCompletionCommand command = new ChatCompletionCommand("任务", "系统指令", List.of(), config);

        gateway.complete(command).block();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        OpenAiChatOptions textOptions = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertNull(textOptions.getResponseFormat());
        assertEquals("planner-model", textOptions.getModel());
        assertEquals(512, textOptions.getMaxTokens());
    }

    @Test
    void shouldExposeFinishReasonAndReasoningLengthWithoutExposingReasoningContent() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        AssistantMessage output = AssistantMessage.builder()
                .content("")
                .properties(Map.of("reasoningContent", "内部推理内容"))
                .build();
        Generation generation = new Generation(output, ChatGenerationMetadata.builder().finishReason("LENGTH").build());
        ChatResponse response = new ChatResponse(List.of(generation));
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder()
                .model("reasoning-model")
                .build());
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        SpringAiChatClientFactory chatClientFactory = new SpringAiChatClientFactory(ObservationRegistry.NOOP);
        SpringAiChatCompletionGateway gateway = new SpringAiChatCompletionGateway(
                factory, chatClientFactory, ObservationRegistry.NOOP);

        ChatCompletionResult<String> result = gateway.complete(
                new ChatCompletionCommand("任务", "系统指令", List.of(), config)).block();

        assertEquals("", result.content());
        assertEquals("LENGTH", result.finishReason());
        assertEquals(6, result.reasoningCharacters());
    }

    private static ChatResponse response() {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage("{}"));
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(mock(Usage.class));
        when(response.getResult()).thenReturn(generation);
        when(response.getMetadata()).thenReturn(metadata);
        return response;
    }
}
