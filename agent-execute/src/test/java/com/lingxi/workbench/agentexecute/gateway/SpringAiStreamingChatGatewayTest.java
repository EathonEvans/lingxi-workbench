package com.lingxi.workbench.agentexecute.gateway;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentProgressPhase;
import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode;
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
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                new SpringAiStreamingChatGateway(factory, ObservationRegistry.NOOP, toolCallingManager());

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
    void shouldEmitOnlyFinalUsageWhenProviderRepeatsCumulativeSnapshot() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        Flux<ChatResponse> responseStream = Flux.just(
                response("你", usage(100, 1, 101)),
                response("好", usage(100, 2, 102)),
                response("", usage(100, 2, 102)));
        when(chatModel.stream(any(Prompt.class))).thenReturn(responseStream);
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        SpringAiStreamingChatGateway gateway = new SpringAiStreamingChatGateway(factory, ObservationRegistry.NOOP, toolCallingManager());

        List<ChatStreamChunk> chunks = gateway.stream(new ChatStreamCommand("你好", List.of(), config))
                .collectList()
                .block();

        assertEquals(List.of(
                new ChatStreamChunk("你"),
                new ChatStreamChunk("好"),
                new ChatStreamChunk("", TokenUsage.of(100, 2, 102))), chunks);
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
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> {
            if (modelCalls.getAndIncrement() == 0) {
                return Flux.just(firstRound);
            }
            return Flux.just(secondRound);
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
                        .maxModelCalls(2)
                        .maxResultCharacters(1000)
                        .eventPublisher(toolEvents::add)
                        .build();
        SpringAiStreamingChatGateway gateway =
                new SpringAiStreamingChatGateway(factory, ObservationRegistry.NOOP, toolCallingManager());

        Flux<ChatStreamChunk> answer = gateway.stream(
                ChatStreamCommand.builder()
                        .prompt("现在几点")
                        .systemPrompt("")
                        .history(List.of())
                        .modelConfig(config)
                        .toolRuntimeConfig(toolConfig)
                        .build());

        verify(chatModel, never()).stream(any(Prompt.class));
        assertEquals(0, executions.get());

        List<ChatStreamChunk> chunks = answer
                .collectList()
                .block();

        assertEquals(1, executions.get());
        assertEquals(
                List.of(AgentToolExecutionStatus.RUNNING, AgentToolExecutionStatus.SUCCEEDED),
                toolEvents.stream().map(AgentToolExecutionEvent::getStatus).toList());
        assertEquals("查询当前时间", toolEvents.get(0).getDisplayName());
        assertEquals(
                List.of(
                        new ChatStreamChunk("", TokenUsage.of(5, 2, 7)),
                        new ChatStreamChunk("现在是北京时间"),
                        new ChatStreamChunk("", TokenUsage.of(8, 3, 11))),
                chunks);
        ArgumentCaptor<Prompt> promptCaptor =
                ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(promptCaptor.capture());
        List<?> secondRoundMessages = promptCaptor.getAllValues()
                .get(1)
                .getInstructions();
        assertEquals(
                1,
                secondRoundMessages.stream()
                        .filter(AssistantMessage.class::isInstance)
                        .map(AssistantMessage.class::cast)
                        .filter(AssistantMessage::hasToolCalls)
                        .count());
        assertEquals(
                1,
                secondRoundMessages.stream()
                        .filter(ToolResponseMessage.class::isInstance)
                        .count());
    }

    @Test
    void shouldReuseDeterministicToolResultAndForceFinalAnswer() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> switch (modelCalls.getAndIncrement()) {
            case 0 -> Flux.just(toolCallResponse("call-1", "{\"zoneId\":\"Asia/Shanghai\"}"));
            case 1 -> Flux.just(toolCallResponse("call-2", "{ \"zoneId\" : \"Asia/Shanghai\" }"));
            default -> Flux.just(response("已基于已有时间信息回答", usage(7, 4, 11)));
        });
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        AtomicInteger executions = new AtomicInteger();
        List<AgentToolExecutionEvent> toolEvents = new ArrayList<>();
        ToolCallback callback = FunctionToolCallback
                .builder("system_current_time", (TimeInput input) -> {
                    executions.incrementAndGet();
                    return Map.of("zoneId", input.zoneId(), "currentTime", "2026-07-29T14:30:00+08:00");
                })
                .description("查询当前时间")
                .inputType(TimeInput.class)
                .build();
        AgentToolRuntimeConfig toolConfig = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool(callback, AgentToolExecutionMode.DETERMINISTIC_READ_ONLY)))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("execution-reuse")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxToolCalls(3)
                .maxModelCalls(4)
                .maxInputTokens(1000)
                .maxOutputTokens(1000)
                .maxResultCharacters(1000)
                .eventPublisher(toolEvents::add)
                .build();
        SpringAiStreamingChatGateway gateway = new SpringAiStreamingChatGateway(factory, ObservationRegistry.NOOP, toolCallingManager());

        List<ChatStreamChunk> chunks = gateway.stream(ChatStreamCommand.builder()
                        .prompt("查询时间")
                        .systemPrompt("")
                        .history(List.of())
                        .modelConfig(config)
                        .toolRuntimeConfig(toolConfig)
                        .build())
                .collectList()
                .block();

        assertEquals(1, executions.get());
        assertEquals(3, modelCalls.get());
        assertEquals(List.of(AgentToolExecutionStatus.RUNNING, AgentToolExecutionStatus.SUCCEEDED,
                AgentToolExecutionStatus.REUSED), toolEvents.stream().map(AgentToolExecutionEvent::getStatus).toList());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("已有时间信息")));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(3)).stream(promptCaptor.capture());
        OpenAiChatOptions finalOptions = (OpenAiChatOptions) promptCaptor.getAllValues().getLast().getOptions();
        assertTrue(finalOptions.getToolCallbacks().isEmpty());
        assertEquals("none", finalOptions.getToolChoice());
    }

    @Test
    void shouldFinalizeWithExistingEvidenceWhenToolLimitIsReached() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> switch (modelCalls.getAndIncrement()) {
            case 0 -> Flux.just(toolCallResponse("call-1", "{\"zoneId\":\"Asia/Shanghai\"}"));
            default -> Flux.just(response("已根据第一次查询结果完成回答", usage(7, 4, 11)));
        });
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        AtomicInteger executions = new AtomicInteger();
        ToolCallback callback = FunctionToolCallback
                .builder("system_current_time", (TimeInput input) -> {
                    executions.incrementAndGet();
                    return Map.of("zoneId", input.zoneId(), "currentTime", "2026-07-29T14:30:00+08:00");
                })
                .description("查询当前时间")
                .inputType(TimeInput.class)
                .build();
        AgentToolRuntimeConfig toolConfig = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool(callback)))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("execution-tool-limit")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxToolCalls(1)
                .maxModelCalls(4)
                .maxTotalTokens(1000)
                .maxFinalizationTokens(1100)
                .maxResultCharacters(1000)
                .build();
        SpringAiStreamingChatGateway gateway = new SpringAiStreamingChatGateway(factory, ObservationRegistry.NOOP, toolCallingManager());

        List<ChatStreamChunk> chunks = gateway.stream(ChatStreamCommand.builder()
                        .prompt("查询两个时区")
                        .systemPrompt("")
                        .history(List.of())
                        .modelConfig(config)
                        .toolRuntimeConfig(toolConfig)
                        .build())
                .collectList()
                .block();

        assertEquals(1, executions.get());
        assertEquals(2, modelCalls.get());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("第一次查询结果")));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(promptCaptor.capture());
        OpenAiChatOptions finalOptions = (OpenAiChatOptions) promptCaptor.getAllValues().getLast().getOptions();
        assertTrue(finalOptions.getToolCallbacks().isEmpty());
        assertEquals("none", finalOptions.getToolChoice());
    }

    @Test
    void shouldRestrictProviderToolBatchAndPublishSafeProgress() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> {
            if (modelCalls.getAndIncrement() == 0) {
                return Flux.just(toolCallResponse(5));
            }
            return Flux.just(response("已基于获批检索结果回答", usage(9, 4, 13)));
        });
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(chatModel);
        AtomicInteger executions = new AtomicInteger();
        List<AgentProgressEvent> progressEvents = new ArrayList<>();
        ToolCallback callback = FunctionToolCallback
                .builder("system_current_time", (TimeInput input) -> {
                    executions.incrementAndGet();
                    return Map.of("zoneId", input.zoneId(), "currentTime", "2026-07-29T14:30:00+08:00");
                })
                .description("查询当前时间")
                .inputType(TimeInput.class)
                .build();
        AgentToolRuntimeConfig toolConfig = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool(callback, AgentToolExecutionMode.DETERMINISTIC_READ_ONLY)))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("execution-batch-limit")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxToolCalls(20)
                .maxModelCalls(4)
                .maxTotalTokens(1000)
                .maxFinalizationTokens(1100)
                .maxResultCharacters(1000)
                .progressPublisher(progressEvents::add)
                .build();
        SpringAiStreamingChatGateway gateway = new SpringAiStreamingChatGateway(
                factory, ObservationRegistry.NOOP, toolCallingManager());

        List<ChatStreamChunk> chunks = gateway.stream(ChatStreamCommand.builder()
                        .prompt("批量查询多个时区")
                        .systemPrompt("")
                        .history(List.of())
                        .modelConfig(config)
                        .toolRuntimeConfig(toolConfig)
                        .build())
                .collectList()
                .block();

        assertEquals(2, executions.get());
        assertEquals(2, modelCalls.get());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("获批检索结果")));
        assertEquals(List.of(
                        AgentProgressPhase.ANALYZING,
                        AgentProgressPhase.USING_TOOLS,
                        AgentProgressPhase.FINALIZING),
                progressEvents.stream().map(AgentProgressEvent::phase).toList());
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(promptCaptor.capture());
        List<AssistantMessage.ToolCall> approvedCalls = promptCaptor.getAllValues().getLast().getInstructions()
                .stream()
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .filter(AssistantMessage::hasToolCalls)
                .findFirst()
                .orElseThrow()
                .getToolCalls();
        assertEquals(2, approvedCalls.size());
    }

    private static AgentTool tool(ToolCallback callback) {
        return tool(callback, AgentToolExecutionMode.SIDE_EFFECT);
    }

    /**
     * 使用默认解析器构建真实工具调用管理器，使测试中的工具回调能够被实际执行。
     */
    private static ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder()
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private static AgentTool tool(ToolCallback callback, AgentToolExecutionMode executionMode) {
        return tool(callback, executionMode, 0, 0);
    }

    private static AgentTool tool(ToolCallback callback, AgentToolExecutionMode executionMode,
                                  int maxCallsPerRound, int maxCallsPerExecution) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("system_current_time")
                .displayName("查询当前时间")
                .description("查询当前时间")
                .executionMode(executionMode)
                .maxCallsPerRound(maxCallsPerRound)
                .maxCallsPerExecution(maxCallsPerExecution)
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
        return toolCallResponse("call-1", "{\"zoneId\":\"Asia/Shanghai\"}");
    }

    private static ChatResponse toolCallResponse(String callId, String arguments) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        callId,
                        "function",
                        "system_current_time",
                        arguments)))
                .build();
        return new ChatResponse(
                List.of(new Generation(assistantMessage)),
                ChatResponseMetadata.builder()
                        .usage(usage(5, 2, 7))
                        .build());
    }

    private static ChatResponse toolCallResponse(int count) {
        List<AssistantMessage.ToolCall> calls = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new AssistantMessage.ToolCall(
                        "call-" + index,
                        "function",
                        "system_current_time",
                        "{\"zoneId\":\"Zone-" + index + "\"}"))
                .toList();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(calls)
                .build();
        return new ChatResponse(
                List.of(new Generation(assistantMessage)),
                ChatResponseMetadata.builder().usage(usage(5, 2, 7)).build());
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
