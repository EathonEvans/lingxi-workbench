package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.checkpoint.ExecutionStateSnapshot;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.gateway.SpringAiStreamingChatGateway;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeLimitException;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeTest {

    @Test
    void shouldCompleteWithMonotonicLifecycleAndUsage() {
        SpringAiStreamingChatGateway gateway = gateway(Flux.just(
                new ChatStreamChunk("你"),
                new ChatStreamChunk("好"),
                new ChatStreamChunk("", TokenUsage.of(8, 2, 10))));
        RecordingSink sink = new RecordingSink();

        AgentRunResult result = new AgentRuntime(gateway).run(request(sink, Duration.ofSeconds(2), 7L));

        assertInstanceOf(AgentRunResult.Completed.class, result);
        assertEquals("你好", result.content());
        assertEquals(10, result.usage().totalTokens());
        assertEquals(List.of(8L, 9L, 10L, 11L, 12L, 13L),
                sink.events.stream().map(ExecutionEvent::getSequenceNo).toList());
        assertEquals(List.of(
                        ExecutionEventType.RUN_STARTED,
                        ExecutionEventType.AGENT_STARTED,
                        ExecutionEventType.MODEL_STARTED,
                        ExecutionEventType.MODEL_COMPLETED,
                        ExecutionEventType.AGENT_COMPLETED,
                        ExecutionEventType.RUN_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldReturnPartialWhenDeadlineHasUsefulOutput() {
        SpringAiStreamingChatGateway gateway = gateway(Flux.concat(
                Flux.just(new ChatStreamChunk("可用结果")), Flux.never()));
        RecordingSink sink = new RecordingSink();

        AgentRunResult result = new AgentRuntime(gateway).run(request(sink, Duration.ofMillis(60), 0L));

        assertInstanceOf(AgentRunResult.Partial.class, result);
        assertEquals("可用结果", result.content());
        assertEquals(ExecutionEventType.RUN_PARTIAL, sink.events.getLast().getType());
    }

    @Test
    void shouldUseGraceOnlyForRecentlyActiveOutput() {
        SpringAiStreamingChatGateway gateway = gateway(Flux.concat(
                Flux.just(new ChatStreamChunk("持续输出")), Flux.never()));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = AgentRunRequest.builder()
                .runId("run-grace")
                .agentId(10L)
                .agentVersionId(20L)
                .modelCommand(command())
                .policy(policy(Duration.ofMillis(40), Duration.ofMillis(80), 100))
                .eventSink(sink)
                .initialUsage(TokenUsage.empty())
                .build();
        long startedAt = System.nanoTime();

        AgentRunResult result = new AgentRuntime(gateway).run(request);

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertInstanceOf(AgentRunResult.Partial.class, result);
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMillis >= 100L);
    }

    @Test
    void shouldFailOnceWhenModelPublisherFails() {
        SpringAiStreamingChatGateway gateway = gateway(Flux.error(new IllegalStateException("provider unavailable")));
        RecordingSink sink = new RecordingSink();

        assertThrows(IllegalStateException.class,
                () -> new AgentRuntime(gateway).run(request(sink, Duration.ofSeconds(2), 0L)));

        assertEquals(1, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_FAILED)
                .count());
    }

    @Test
    void shouldFailWhenModelCompletesWithoutUsefulText() {
        SpringAiStreamingChatGateway gateway = gateway(Flux.just(
                new ChatStreamChunk("", TokenUsage.of(8, 0, 8))));
        RecordingSink sink = new RecordingSink();

        assertThrows(IllegalStateException.class,
                () -> new AgentRuntime(gateway).run(request(sink, Duration.ofSeconds(2), 0L)));

        assertEquals(ExecutionEventType.RUN_FAILED, sink.events.getLast().getType());
        assertEquals(0, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_COMPLETED)
                .count());
    }

    @Test
    void shouldMarkApplicationFinalizationAsReservedFinalModelCall() {
        SpringAiStreamingChatGateway gateway = mock(SpringAiStreamingChatGateway.class);
        when(gateway.stream(any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            ExecutionState state = invocation.getArgument(1);
            assertTrue(state.isFinalizationRequested());
            int callNo = state.beforeFinalModelCall();
            return Flux.just(new ChatStreamChunk("收尾回答", TokenUsage.of(5, 2, 7)))
                    .doOnComplete(() -> state.completeModelCall(callNo, TokenUsage.of(5, 2, 7), true));
        }));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = AgentRunRequest.builder()
                .runId("run-finalization")
                .agentId(10L)
                .agentVersionId(20L)
                .modelCommand(command())
                .finalizationOnly(true)
                .policy(policy(Duration.ofSeconds(2), Duration.ZERO, 100))
                .eventSink(sink)
                .initialUsage(TokenUsage.empty())
                .build();

        AgentRunResult result = new AgentRuntime(gateway).run(request);

        assertEquals("收尾回答", result.content());
        assertEquals(List.of(ExecutionEventType.RUN_STARTED, ExecutionEventType.AGENT_STARTED,
                        ExecutionEventType.FINALIZING, ExecutionEventType.MODEL_STARTED,
                        ExecutionEventType.MODEL_COMPLETED, ExecutionEventType.AGENT_COMPLETED,
                        ExecutionEventType.RUN_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldRejectModelCallWhenTokenBudgetIsAlreadyExhausted() {
        SpringAiStreamingChatGateway gateway = mock(SpringAiStreamingChatGateway.class);
        when(gateway.stream(any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            ExecutionState state = invocation.getArgument(1);
            state.beforeModelCall();
            return Flux.empty();
        }));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = AgentRunRequest.builder()
                .runId("run-budget")
                .agentId(10L)
                .agentVersionId(20L)
                .modelCommand(command())
                .policy(policy(Duration.ofSeconds(2), Duration.ZERO, 10))
                .eventSink(sink)
                .initialUsage(TokenUsage.of(5, 5, 10))
                .build();

        assertThrows(AgentRuntimeLimitException.class, () -> new AgentRuntime(gateway).run(request));
        assertEquals(ExecutionEventType.RUN_FAILED, sink.events.getLast().getType());
    }

    @Test
    void shouldCancelWithoutPublishingFailure() {
        SpringAiStreamingChatGateway gateway = gateway(Flux.never());
        RecordingSink sink = new RecordingSink();
        sink.cancelled.set(true);

        assertThrows(AgentRunCancelledException.class,
                () -> new AgentRuntime(gateway).run(request(sink, Duration.ofSeconds(2), 0L)));

        assertEquals(ExecutionEventType.RUN_CANCELLED, sink.events.getLast().getType());
        assertEquals(0, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_FAILED)
                .count());
    }

    @Test
    void shouldReturnTypedInterruptionWithoutPublishingFailure() {
        SpringAiStreamingChatGateway gateway = mock(SpringAiStreamingChatGateway.class);
        when(gateway.stream(any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            ExecutionState state = invocation.getArgument(1);
            state.interrupt(RuntimeInterruption.builder().interactionId(91L).fingerprint("fp").build());
            return Flux.empty();
        }));
        RecordingSink sink = new RecordingSink();

        AgentRunResult result = new AgentRuntime(gateway).run(
                request(sink, Duration.ofSeconds(2), 0L));

        AgentRunResult.Interrupted interrupted = assertInstanceOf(AgentRunResult.Interrupted.class, result);
        assertEquals(91L, interrupted.interruption().getInteractionId());
        assertEquals(0, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_FAILED).count());
    }

    @Test
    void shouldDelegateResumeToCheckpointPathWithoutReplayingInitialModelCall() {
        SpringAiStreamingChatGateway gateway = mock(SpringAiStreamingChatGateway.class);
        when(gateway.resume(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Flux.just(new ChatStreamChunk("恢复完成")));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = request(sink, Duration.ofSeconds(2), 4L);
        RuntimeCheckpoint checkpoint = RuntimeCheckpoint.builder()
                .version(RuntimeCheckpoint.CURRENT_VERSION)
                .fingerprint("fp")
                .state(ExecutionStateSnapshot.builder()
                        .runId("run-1").agentId(10L).agentVersionId(20L)
                        .maxModelCalls(1).maxToolCalls(0).maxTotalTokens(100).maxFinalizationTokens(100)
                        .timeoutMillis(2000L).activeOutputGraceMillis(0L)
                        .modelCalls(0).toolCalls(0).tokenUsage(TokenUsage.empty())
                        .lastSequenceNo(4L).activeElapsedMillis(0L)
                        .finalizationRequested(false).output("")
                        .toolCallCounts(List.of()).toolLedger(List.of()).skills(List.of())
                        .authorizedToolCodes(List.of()).build())
                .build();

        AgentRunResult result = new AgentRuntime(gateway).resume(
                new AgentResumeRequest(request, checkpoint, 91L, "fp"));

        assertEquals("恢复完成", result.content());
        verify(gateway, never()).stream(any(), any());
        verify(gateway).resume(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(91L), org.mockito.ArgumentMatchers.eq("fp"));
    }

    private static SpringAiStreamingChatGateway gateway(Flux<ChatStreamChunk> chunks) {
        SpringAiStreamingChatGateway gateway = mock(SpringAiStreamingChatGateway.class);
        when(gateway.stream(any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            ExecutionState state = invocation.getArgument(1);
            int callNo = state.beforeModelCall();
            return chunks.doOnComplete(() -> state.completeModelCall(callNo, TokenUsage.of(8, 2, 10), false));
        }));
        return gateway;
    }

    private static AgentRunRequest request(RecordingSink sink, Duration timeout, long initialSequence) {
        return AgentRunRequest.builder()
                .runId("run-1")
                .agentId(10L)
                .agentVersionId(20L)
                .modelCommand(command())
                .policy(policy(timeout, Duration.ZERO, 100))
                .eventSink(sink)
                .initialSequenceNo(initialSequence)
                .initialUsage(TokenUsage.empty())
                .build();
    }

    private static ChatStreamCommand command() {
        return ChatStreamCommand.builder()
                .prompt("你好")
                .systemPrompt("你是助手")
                .history(List.of())
                .modelConfig(ChatModelRuntimeConfig.builder().build())
                .build();
    }

    private static ExecutionPolicy policy(Duration timeout, Duration activeOutputGrace, int maxTokens) {
        return ExecutionPolicy.builder()
                .maxModelCalls(1)
                .maxToolCalls(0)
                .maxTotalTokens(maxTokens)
                .maxFinalizationTokens(maxTokens)
                .timeout(timeout)
                .activeOutputGrace(activeOutputGrace)
                .build();
    }

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<ExecutionEvent> events = new ArrayList<>();
        private final List<ChatStreamChunk> chunks = new ArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void onEvent(ExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
