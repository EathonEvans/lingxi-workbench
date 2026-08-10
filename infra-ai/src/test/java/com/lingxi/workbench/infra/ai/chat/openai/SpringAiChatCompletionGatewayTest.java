package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

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
        SpringAiChatCompletionGateway gateway = new SpringAiChatCompletionGateway(factory);
        ChatCompletionCommand command = new ChatCompletionCommand("任务", "系统指令", List.of(), config);

        gateway.complete(command).block();
        gateway.completeJson(command).block();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        assertNull(promptCaptor.getAllValues().get(0).getOptions());
        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getAllValues().get(1).getOptions();
        assertEquals(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT, options.getResponseFormat().getType());
        assertNull(options.getResponseFormat().getJsonSchema());
        assertEquals("planner-model", options.getModel());
        assertEquals(512, options.getMaxTokens());
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
