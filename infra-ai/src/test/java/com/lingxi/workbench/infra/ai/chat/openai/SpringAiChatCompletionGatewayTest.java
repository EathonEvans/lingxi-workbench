package com.lingxi.workbench.infra.ai.chat.openai;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiChatCompletionGatewayTest {

    @Test
    void shouldApplyJsonObjectOnlyToJsonCompletion() {
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
        SpringAiChatCompletionGateway gateway = new SpringAiChatCompletionGateway(factory, ObservationRegistry.NOOP);
        ChatCompletionCommand command = new ChatCompletionCommand("任务", "系统指令", List.of(), config);

        gateway.complete(command).block();
        ChatCompletionResult<JSONObject> jsonResult = gateway.completeJson(command).block();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        assertNull(promptCaptor.getAllValues().get(0).getOptions());
        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getAllValues().get(1).getOptions();
        assertEquals(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT, options.getResponseFormat().getType());
        assertNull(options.getResponseFormat().getJsonSchema());
        assertEquals("planner-model", options.getModel());
        assertEquals(512, options.getMaxTokens());
        assertEquals(new JSONObject(), jsonResult.content());
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
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        SpringAiChatCompletionGateway gateway = new SpringAiChatCompletionGateway(factory, ObservationRegistry.NOOP);

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
