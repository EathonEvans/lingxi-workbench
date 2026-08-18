package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.checkpoint.HumanApprovalRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointContext;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.infra.ai.tool.model.ToolSecurityPolicy;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiStreamingChatGatewayTest {

    @Test
    void shouldUseRuntimeModelAndConvertNonEmptyChunks() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = model();
        Usage finalUsage = usage(10, 4, 14);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("你"), response(""), response("好"), response("", finalUsage)));
        SpringAiStreamingChatGateway gateway = gateway(factory(config, chatModel));

        List<ChatStreamChunk> chunks = gateway.stream(new ChatStreamCommand(
                        "你好",
                        List.of(new ChatHistoryMessage(ChatHistoryMessage.Role.USER, "上一问"),
                                new ChatHistoryMessage(ChatHistoryMessage.Role.ASSISTANT, "上一答")), config))
                .collectList().block();

        assertEquals(List.of(new ChatStreamChunk("你"), new ChatStreamChunk("好"),
                new ChatStreamChunk("", TokenUsage.of(10, 4, 14))), chunks);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        List<?> messages = promptCaptor.getValue().getInstructions();
        assertEquals("上一问", ((UserMessage) messages.get(1)).getText());
        assertEquals("上一答", ((AssistantMessage) messages.get(2)).getText());
        assertEquals("你好", ((UserMessage) messages.get(3)).getText());
    }

    @Test
    void shouldExecuteSpringAiToolLoopThroughSingleExecutionState() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = model();
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> modelCalls.getAndIncrement() == 0
                ? Flux.just(toolCallResponse("call-1", "{\"zoneId\":\"Asia/Shanghai\"}"))
                : Flux.just(response("现在是北京时间", usage(8, 3, 11))));
        AtomicInteger executions = new AtomicInteger();
        AgentToolRuntimeConfig toolConfig = toolConfig(tool(callback(executions)), "execution-1");
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(toolConfig, sink, 3, 2);
        SpringAiStreamingChatGateway gateway = gateway(factory(config, chatModel));

        Flux<ChatStreamChunk> answer = gateway.stream(command(config, toolConfig, "现在几点"), state);
        verify(chatModel, never()).stream(any(Prompt.class));
        List<ChatStreamChunk> chunks = answer.collectList().block();

        assertEquals(1, executions.get());
        assertEquals(2, state.modelCalls());
        assertEquals(1, state.toolCalls());
        assertEquals(List.of(new ChatStreamChunk("现在是北京时间", TokenUsage.of(8, 3, 11))), chunks);
        assertEquals(List.of(ExecutionEventType.MODEL_STARTED, ExecutionEventType.MODEL_COMPLETED,
                        ExecutionEventType.TOOL_STARTED, ExecutionEventType.TOOL_COMPLETED,
                        ExecutionEventType.MODEL_STARTED, ExecutionEventType.MODEL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(promptCaptor.capture());
        List<?> messages = promptCaptor.getAllValues().get(1).getInstructions();
        assertEquals(1, messages.stream().filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast).filter(AssistantMessage::hasToolCalls).count());
        assertEquals(1, messages.stream().filter(ToolResponseMessage.class::isInstance).count());
    }

    @Test
    void shouldReuseDeterministicToolResultAndForceFinalAnswer() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = model();
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> switch (modelCalls.getAndIncrement()) {
            case 0 -> Flux.just(toolCallResponse("call-1", "{\"zoneId\":\"Asia/Shanghai\"}"));
            case 1 -> Flux.just(toolCallResponse("call-2", "{ \"zoneId\" : \"Asia/Shanghai\" }"));
            default -> Flux.just(response("已基于已有时间信息回答", usage(7, 4, 11)));
        });
        AtomicInteger executions = new AtomicInteger();
        AgentToolRuntimeConfig toolConfig = toolConfig(
                tool(callback(executions), AgentToolExecutionMode.DETERMINISTIC_READ_ONLY), "execution-reuse");
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(toolConfig, sink, 4, 3);

        List<ChatStreamChunk> chunks = gateway(factory(config, chatModel))
                .stream(command(config, toolConfig, "查询时间"), state).collectList().block();

        assertEquals(1, executions.get());
        assertEquals(3, modelCalls.get());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("已有时间信息")));
        assertEquals(1, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.TOOL_REUSED).count());
        assertEquals(1, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.FINALIZING).count());
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(3)).stream(promptCaptor.capture());
        OpenAiChatOptions finalOptions = (OpenAiChatOptions) promptCaptor.getAllValues().getLast().getOptions();
        assertTrue(finalOptions.getToolCallbacks().isEmpty());
        assertEquals("none", finalOptions.getToolChoice());
    }

    @Test
    void shouldFinalizeAfterGlobalToolLimit() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = model();
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> modelCalls.getAndIncrement() == 0
                ? Flux.just(toolCallResponse("call-1", "{\"zoneId\":\"Asia/Shanghai\"}"))
                : Flux.just(response("已根据查询结果完成回答", usage(7, 4, 11))));
        AtomicInteger executions = new AtomicInteger();
        AgentToolRuntimeConfig toolConfig = toolConfig(tool(callback(executions)), "execution-limit");
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(toolConfig, sink, 4, 1);

        List<ChatStreamChunk> chunks = gateway(factory(config, chatModel))
                .stream(command(config, toolConfig, "查询时间"), state).collectList().block();

        assertEquals(1, executions.get());
        assertEquals(2, modelCalls.get());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("查询结果")));
        assertEquals(1, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.FINALIZING).count());
    }

    @Test
    void shouldRestrictProviderToolBatchToDefinitionLimit() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = model();
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> modelCalls.getAndIncrement() == 0
                ? Flux.just(toolCallResponse(5))
                : Flux.just(response("已基于获批结果回答", usage(9, 4, 13))));
        AtomicInteger executions = new AtomicInteger();
        AgentToolRuntimeConfig toolConfig = toolConfig(tool(callback(executions)), "execution-batch");
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(toolConfig, sink, 4, 10);

        gateway(factory(config, chatModel)).stream(command(config, toolConfig, "批量查询"), state)
                .collectList().block();

        assertEquals(2, executions.get());
        assertEquals(2, state.toolCalls());
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(promptCaptor.capture());
        List<AssistantMessage.ToolCall> approvedCalls = promptCaptor.getAllValues().getLast().getInstructions()
                .stream().filter(AssistantMessage.class::isInstance).map(AssistantMessage.class::cast)
                .filter(AssistantMessage::hasToolCalls).findFirst().orElseThrow().getToolCalls();
        assertEquals(2, approvedCalls.size());
    }

    @Test
    void shouldResumeOriginalApprovedToolCallBeforeNextModelCall() {
        ChatModelRuntimeConfig config = config();
        OpenAiChatModel chatModel = model();
        AtomicInteger modelCalls = new AtomicInteger();
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> modelCalls.getAndIncrement() == 0
                ? Flux.just(toolCallResponse("call-approved", "{\"zoneId\":\"Asia/Shanghai\"}"))
                : Flux.just(response("审批后已执行", usage(8, 3, 11))));
        AtomicInteger executions = new AtomicInteger();
        AgentToolRuntimeConfig toolConfig = toolConfig(approvalTool(callback(executions)), "execution-hitl");
        RuntimeCheckpointStore store = mock(RuntimeCheckpointStore.class);
        AtomicReference<RuntimeCheckpoint> capturedCheckpoint = new AtomicReference<>();
        when(store.interrupt(any(HumanApprovalRequest.class))).thenAnswer(invocation -> {
            HumanApprovalRequest request = invocation.getArgument(0);
            capturedCheckpoint.set(request.checkpoint());
            return RuntimeInterruption.builder()
                    .interactionId(91L).checkpointId(92L)
                    .fingerprint(request.checkpoint().getFingerprint()).build();
        });
        when(store.claimApproved(any(RuntimeCheckpoint.class),
                org.mockito.ArgumentMatchers.eq(91L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(93L);
        RecordingSink sink = new RecordingSink();
        ExecutionState first = state(toolConfig, sink, 3, 2, store,
                RuntimeCheckpointContext.builder()
                        .query("查询时间").capabilityCode("time_agent").applicationVersionId(30L)
                        .userId(1L).conversationId(2L).assistantMessageId(3L).build());
        SpringAiStreamingChatGateway gateway = gateway(factory(config, chatModel));

        List<ChatStreamChunk> interrupted = gateway.stream(
                command(config, toolConfig, "查询时间"), first).collectList().block();

        assertTrue(interrupted.isEmpty());
        assertEquals(0, executions.get());
        RuntimeCheckpoint checkpoint = capturedCheckpoint.get();
        ExecutionState resumed = state(toolConfig, sink, 3, 2, store, checkpoint.getContext(), checkpoint);

        List<ChatStreamChunk> chunks = gateway.resume(command(config, toolConfig, "ignored"), resumed,
                checkpoint, 91L, checkpoint.getFingerprint()).collectList().block();

        assertEquals(1, executions.get());
        assertEquals(List.of(new ChatStreamChunk("审批后已执行", TokenUsage.of(8, 3, 11))), chunks);
        verify(store).complete(org.mockito.ArgumentMatchers.eq(93L),
                org.mockito.ArgumentMatchers.contains("2026-07-29T14:30:00+08:00"));
        verify(chatModel, times(2)).stream(any(Prompt.class));
    }

    private static SpringAiStreamingChatGateway gateway(OpenAiCompatibleChatModelFactory factory) {
        return new SpringAiStreamingChatGateway(factory, ObservationRegistry.NOOP, toolCallingManager());
    }

    private static ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder().observationRegistry(ObservationRegistry.NOOP).build();
    }

    private static OpenAiChatModel model() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        return model;
    }

    private static OpenAiCompatibleChatModelFactory factory(ChatModelRuntimeConfig config, OpenAiChatModel model) {
        OpenAiCompatibleChatModelFactory factory = mock(OpenAiCompatibleChatModelFactory.class);
        when(factory.create(config)).thenReturn(model);
        return factory;
    }

    private static ExecutionState state(AgentToolRuntimeConfig toolConfig, RecordingSink sink,
                                        int maxModelCalls, int maxToolCalls) {
        return state(toolConfig, sink, maxModelCalls, maxToolCalls, null, null);
    }

    private static ExecutionState state(AgentToolRuntimeConfig toolConfig, RecordingSink sink,
                                        int maxModelCalls, int maxToolCalls, RuntimeCheckpointStore store,
                                        RuntimeCheckpointContext checkpointContext) {
        return ExecutionState.builder()
                .runId(toolConfig.getExecutionContext().getExecutionId())
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(maxModelCalls)
                        .maxToolCalls(maxToolCalls)
                        .maxTotalTokens(1000)
                        .maxFinalizationTokens(1100)
                        .timeout(Duration.ofSeconds(3))
                        .activeOutputGrace(Duration.ZERO)
                        .build())
                .eventSink(sink)
                .toolRuntimeConfig(toolConfig)
                .authorizedToolCodes(List.of("system_current_time"))
                .checkpointStore(store)
                .checkpointContext(checkpointContext)
                .build();
    }

    private static ExecutionState state(AgentToolRuntimeConfig toolConfig, RecordingSink sink,
                                        int maxModelCalls, int maxToolCalls, RuntimeCheckpointStore store,
                                        RuntimeCheckpointContext checkpointContext, RuntimeCheckpoint checkpoint) {
        ExecutionState state = ExecutionState.builder()
                .runId(checkpoint.getState().getRunId())
                .agentId(10L).agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(maxModelCalls).maxToolCalls(maxToolCalls)
                        .maxTotalTokens(1000).maxFinalizationTokens(1100)
                        .timeout(Duration.ofSeconds(3)).activeOutputGrace(Duration.ZERO).build())
                .eventSink(sink).toolRuntimeConfig(toolConfig)
                .authorizedToolCodes(List.of("system_current_time"))
                .initialSequenceNo(checkpoint.getState().getLastSequenceNo())
                .initialModelCalls(checkpoint.getState().getModelCalls())
                .initialToolCalls(checkpoint.getState().getToolCalls())
                .initialUsage(checkpoint.getState().getTokenUsage())
                .checkpointStore(store).checkpointContext(checkpointContext)
                .build();
        state.restore(checkpoint.getState(), List.of());
        return state;
    }

    private static ChatStreamCommand command(ChatModelRuntimeConfig config, AgentToolRuntimeConfig toolConfig,
                                             String prompt) {
        return ChatStreamCommand.builder()
                .prompt(prompt)
                .systemPrompt("")
                .history(List.of())
                .modelConfig(config)
                .toolRuntimeConfig(toolConfig)
                .build();
    }

    private static AgentToolRuntimeConfig toolConfig(AgentTool tool, String executionId) {
        return AgentToolRuntimeConfig.builder()
                .tools(List.of(tool))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId(executionId)
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxResultCharacters(1000)
                .build();
    }

    private static ToolCallback callback(AtomicInteger executions) {
        return FunctionToolCallback.builder("system_current_time", (TimeInput input) -> {
                    executions.incrementAndGet();
                    return Map.of("zoneId", input.zoneId(), "currentTime", "2026-07-29T14:30:00+08:00");
                })
                .description("查询当前时间")
                .inputType(TimeInput.class)
                .build();
    }

    private static AgentTool tool(ToolCallback callback) {
        return tool(callback, AgentToolExecutionMode.SIDE_EFFECT);
    }

    private static AgentTool tool(ToolCallback callback, AgentToolExecutionMode mode) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("system_current_time")
                .displayName("查询当前时间")
                .description("查询当前时间")
                .executionMode(mode)
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

    private static AgentTool approvalTool(ToolCallback callback) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("system_current_time")
                .displayName("查询当前时间")
                .description("查询当前时间")
                .configurationHash("time-v1")
                .securityPolicy(ToolSecurityPolicy.builder()
                        .approvalRequired(true).approvalTtlSeconds(300)
                        .riskLevel(ToolRiskLevel.HIGH).resourceScope("当前会话").build())
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
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))),
                ChatResponseMetadata.builder().usage(usage).build());
    }

    private static ChatResponse toolCallResponse(String callId, String arguments) {
        AssistantMessage message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        callId, "function", "system_current_time", arguments)))
                .build();
        return new ChatResponse(List.of(new Generation(message)),
                ChatResponseMetadata.builder().usage(usage(5, 2, 7)).build());
    }

    private static ChatResponse toolCallResponse(int count) {
        List<AssistantMessage.ToolCall> calls = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new AssistantMessage.ToolCall("call-" + index, "function",
                        "system_current_time", "{\"zoneId\":\"Zone-" + index + "\"}"))
                .toList();
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(calls).build();
        return new ChatResponse(List.of(new Generation(message)),
                ChatResponseMetadata.builder().usage(usage(5, 2, 7)).build());
    }

    private static Usage usage(int promptTokens, int completionTokens, int totalTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        when(usage.getTotalTokens()).thenReturn(totalTokens);
        return usage;
    }

    private record TimeInput(String zoneId) {
    }

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<ExecutionEvent> events = new ArrayList<>();

        @Override
        public void onEvent(ExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
