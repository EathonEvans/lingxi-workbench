package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
                new ChatStreamChunk("", TokenUsage.of(10, 4, 14))), chunks);
        verify(factory).create(config);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        List<?> messages = promptCaptor.getValue().getInstructions();
        assertEquals(4, messages.size());
        assertEquals("上一问", ((UserMessage) messages.get(1)).getText());
        assertEquals("上一答", ((AssistantMessage) messages.get(2)).getText());
        assertEquals("你好", ((UserMessage) messages.get(3)).getText());
    }

    @Test
    void shouldExecuteSpringAiToolLoopAndReturnFinalAnswer() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(
                OpenAiChatOptions.builder()
                        .model("test-model")
                        .build());
        ChatResponse firstRound = toolCallResponse();
        ChatResponse secondRound = response(
                "现在是北京时间", usage(8, 3, 11));
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicReference<String> modelCallThread = new AtomicReference<>();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            modelCallThread.compareAndSet(
                    null,
                    Thread.currentThread().getName());
            if (modelCalls.getAndIncrement() == 0) {
                return firstRound;
            }
            return secondRound;
        });

        OpenAiCompatibleChatModelFactory factory =
                mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        AtomicInteger executions = new AtomicInteger();
        List<AgentToolExecutionEvent> toolEvents = new ArrayList<>();
        ToolCallback callback = FunctionToolCallback
                .builder("system_current_time", (TimeInput input) -> {
                    executions.incrementAndGet();
                    return Map.of(
                            "zoneId", input.zoneId(),
                            "currentTime", "2026-07-29T14:30:00+08:00");
                })
                .description("查询当前时间")
                .inputType(TimeInput.class)
                .build();
        AgentToolRuntimeConfig toolConfig =
                AgentToolRuntimeConfig.builder()
                        .tools(List.of(tool(callback)))
                        .executionContext(AgentToolExecutionContext.builder()
                                .executionId("execution-1")
                                .userId(1L)
                                .conversationId(2L)
                                .assistantMessageId(3L)
                                .build())
                        .maxToolCalls(2)
                        .maxModelCalls(3)
                        .maxResultCharacters(1000)
                        .eventPublisher(toolEvents::add)
                        .build();
        SpringAiStreamingChatGateway gateway =
                new SpringAiStreamingChatGateway(factory);

        Flux<ChatStreamChunk> answer = gateway.stream(
                ChatStreamCommand.builder()
                        .prompt("现在几点")
                        .systemPrompt("")
                        .history(List.of())
                        .modelConfig(config)
                        .toolRuntimeConfig(toolConfig)
                        .build());

        verify(chatModel, never()).call(any(Prompt.class));
        assertEquals(0, executions.get());

        List<ChatStreamChunk> chunks = answer
                .collectList()
                .block();

        assertEquals(1, executions.get());
        assertEquals(
                List.of(AgentToolExecutionStatus.RUNNING, AgentToolExecutionStatus.SUCCEEDED),
                toolEvents.stream().map(AgentToolExecutionEvent::getStatus).toList());
        assertEquals("查询当前时间", toolEvents.get(0).getDisplayName());
        assertNotEquals(
                Thread.currentThread().getName(),
                modelCallThread.get());
        assertEquals(
                List.of(new ChatStreamChunk("现在是北京时间", TokenUsage.of(13, 5, 18))),
                chunks);
        ArgumentCaptor<Prompt> promptCaptor =
                ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        List<?> secondRoundMessages = promptCaptor.getAllValues()
                .get(1)
                .getInstructions();
        assertEquals(
                1,
                secondRoundMessages.stream()
                        .filter(ToolResponseMessage.class::isInstance)
                        .count());
    }

    private static AgentTool tool(ToolCallback callback) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("system_current_time")
                .displayName("查询当前时间")
                .description("查询当前时间")
                .build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public ToolCallback callback() {
                return callback;
            }
        };
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

    private static ChatResponse toolCallResponse() {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "system_current_time",
                        "{\"zoneId\":\"Asia/Shanghai\"}")))
                .build();
        return new ChatResponse(
                List.of(new Generation(assistantMessage)),
                ChatResponseMetadata.builder()
                        .usage(usage(5, 2, 7))
                        .build());
    }

    private static Usage usage(
            int promptTokens,
            int completionTokens,
            int totalTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        when(usage.getTotalTokens()).thenReturn(totalTokens);
        return usage;
    }

    private record TimeInput(String zoneId) {
    }
}
