package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.gateway.AgentScopeSpecialistGateway;
import com.lingxi.workbench.agentruntime.gateway.AgentScopeSupervisorGateway;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeLimitException;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionInterruption;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        AgentScopeSpecialistGateway gateway = gateway(Flux.just(
                new ChatStreamChunk("你"),
                new ChatStreamChunk("好"),
                new ChatStreamChunk("", TokenUsage.of(8, 2, 10))));
        RecordingSink sink = new RecordingSink();

        AgentRunResult result = runtime(gateway).run(request(sink, 7L));

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
    void shouldKeepRunLifecycleOpenForDelegatedInvocation() {
        AgentScopeSpecialistGateway gateway = gateway(Flux.just(new ChatStreamChunk("子任务完成")));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = AgentRunRequest.builder()
                .runId("run-supervisor")
                .agentId(11L)
                .agentVersionId(21L)
                .role(AgentRole.SPECIALIST)
                .scope(ExecutionScope.INVOCATION)
                .modelCommand(command())
                .userId("1")
                .sessionId("run-supervisor")
                .policy(policy(100))
                .eventSink(sink)
                .initialUsage(TokenUsage.empty())
                .build();

        AgentRunResult result = runtime(gateway).run(request);

        assertEquals("子任务完成", result.content());
        assertEquals(List.of(ExecutionEventType.AGENT_STARTED, ExecutionEventType.MODEL_STARTED,
                        ExecutionEventType.MODEL_COMPLETED, ExecutionEventType.AGENT_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldAllowContinuedModelOutputWithoutWallClockDeadline() {
        AgentScopeSpecialistGateway gateway = gateway(Flux.just(
                new ChatStreamChunk("持续"), new ChatStreamChunk("输出"))
                .delayElements(Duration.ofMillis(60)));
        RecordingSink sink = new RecordingSink();

        long startedAt = System.nanoTime();
        AgentRunResult result = runtime(gateway).run(request(sink, 0L));

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertInstanceOf(AgentRunResult.Completed.class, result);
        assertEquals("持续输出", result.content());
        assertTrue(elapsedMillis >= 100L);
    }

    @Test
    void shouldFailOnceWhenModelPublisherFails() {
        AgentScopeSpecialistGateway gateway = gateway(Flux.error(new IllegalStateException("provider unavailable")));
        RecordingSink sink = new RecordingSink();

        assertThrows(IllegalStateException.class,
                () -> runtime(gateway).run(request(sink, 0L)));

        assertEquals(1, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_FAILED)
                .count());
    }

    @Test
    void shouldFailWhenModelCompletesWithoutUsefulText() {
        AgentScopeSpecialistGateway gateway = gateway(Flux.just(
                new ChatStreamChunk("", TokenUsage.of(8, 0, 8))));
        RecordingSink sink = new RecordingSink();

        com.lingxi.workbench.agentruntime.model.AgentRuntimeException exception = assertThrows(
                com.lingxi.workbench.agentruntime.model.AgentRuntimeException.class,
                () -> runtime(gateway).run(request(sink, 0L)));

        assertEquals("AGENT_EMPTY_RESPONSE", exception.getCode());
        assertFalse(exception.isRetryable());
        assertEquals(ExecutionEventType.RUN_FAILED, sink.events.getLast().getType());
        assertEquals(0, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_COMPLETED)
                .count());
    }

    @Test
    void shouldMarkApplicationFinalizationAsReservedFinalModelCall() {
        AgentScopeSpecialistGateway gateway = mock(AgentScopeSpecialistGateway.class);
        when(gateway.stream(any(), any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            io.agentscope.core.agent.RuntimeContext context = invocation.getArgument(1);
            ExecutionBudget budget = context.get(ExecutionBudget.class);
            ExecutionEvents events = context.get(ExecutionEvents.class);
            assertTrue(budget.isFinalizationRequested());
            int callNo = budget.reserveFinalModelCall();
            events.modelStarted(callNo, 9L, "test-model", true);
            return Flux.just(new ChatStreamChunk("收尾回答", TokenUsage.of(5, 2, 7)))
                    .doOnComplete(() -> {
                        TokenUsage usage = TokenUsage.of(5, 2, 7);
                        budget.completeModelCall(usage);
                        events.modelCompleted(callNo, 9L, "test-model", usage, true, null);
                    });
        }));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = AgentRunRequest.builder()
                .runId("run-finalization")
                .agentId(10L)
                .agentVersionId(20L)
                .role(AgentRole.SPECIALIST)
                .modelCommand(command())
                .userId("1")
                .sessionId("run-finalization")
                .finalizationOnly(true)
                .policy(policy(100))
                .eventSink(sink)
                .initialUsage(TokenUsage.empty())
                .build();

        AgentRunResult result = runtime(gateway).run(request);

        assertEquals("收尾回答", result.content());
        assertEquals(List.of(ExecutionEventType.RUN_STARTED, ExecutionEventType.AGENT_STARTED,
                        ExecutionEventType.FINALIZING, ExecutionEventType.MODEL_STARTED,
                        ExecutionEventType.MODEL_COMPLETED, ExecutionEventType.AGENT_COMPLETED,
                        ExecutionEventType.RUN_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldRejectModelCallWhenTokenBudgetIsAlreadyExhausted() {
        AgentScopeSpecialistGateway gateway = mock(AgentScopeSpecialistGateway.class);
        when(gateway.stream(any(), any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            io.agentscope.core.agent.RuntimeContext context = invocation.getArgument(1);
            context.get(ExecutionBudget.class).reserveModelCall();
            return Flux.empty();
        }));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = AgentRunRequest.builder()
                .runId("run-budget")
                .agentId(10L)
                .agentVersionId(20L)
                .role(AgentRole.SPECIALIST)
                .modelCommand(command())
                .userId("1")
                .sessionId("run-budget")
                .policy(policy(10))
                .eventSink(sink)
                .initialUsage(TokenUsage.of(5, 5, 10))
                .build();

        assertThrows(AgentRuntimeLimitException.class, () -> runtime(gateway).run(request));
        assertEquals(ExecutionEventType.RUN_FAILED, sink.events.getLast().getType());
    }

    @Test
    void shouldCancelWithoutPublishingFailure() {
        AgentScopeSpecialistGateway gateway = gateway(Flux.never());
        RecordingSink sink = new RecordingSink();
        sink.cancelled.set(true);

        assertThrows(AgentRunCancelledException.class,
                () -> runtime(gateway).run(request(sink, 0L)));

        assertEquals(ExecutionEventType.RUN_CANCELLED, sink.events.getLast().getType());
        assertEquals(0, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_FAILED)
                .count());
    }

    @Test
    void shouldReturnTypedInterruptionWithoutPublishingFailure() {
        AgentScopeSpecialistGateway gateway = mock(AgentScopeSpecialistGateway.class);
        when(gateway.stream(any(), any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            io.agentscope.core.agent.RuntimeContext context = invocation.getArgument(1);
            RuntimeInterruption interruption = RuntimeInterruption.builder().interactionId(91L)
                    .fingerprint("fp").build();
            context.get(ExecutionInterruption.class).set(interruption);
            context.get(ExecutionEvents.class).humanRequired(interruption);
            return Flux.empty();
        }));
        RecordingSink sink = new RecordingSink();

        AgentRunResult result = runtime(gateway).run(
                request(sink, 0L));

        AgentRunResult.Interrupted interrupted = assertInstanceOf(AgentRunResult.Interrupted.class, result);
        assertEquals(91L, interrupted.interruption().getInteractionId());
        assertEquals(0, sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.RUN_FAILED).count());
    }

    @Test
    void shouldDelegateResumeToCheckpointPathWithoutReplayingInitialModelCall() {
        AgentScopeSpecialistGateway agentScopeGateway = mock(AgentScopeSpecialistGateway.class);
        when(agentScopeGateway.resume(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Flux.just(new ChatStreamChunk("恢复完成")));
        RecordingSink sink = new RecordingSink();
        AgentRunRequest request = request(sink, 4L);
        AgentScopeCheckpoint checkpoint = AgentScopeCheckpoint.builder()
                .version(AgentScopeCheckpoint.CURRENT_VERSION)
                .fingerprint("fp")
                .runId("run-1").agentSessionId("run-1").agentId(10L).agentVersionId(20L)
                .maxModelCalls(1).maxToolCalls(0).maxTotalTokens(100).maxFinalizationTokens(100)
                .modelCalls(0).toolCalls(0).tokenUsage(TokenUsage.empty())
                .lastSequenceNo(4L)
                .finalizationRequested(false).output("")
                .toolCallCounts(List.of())
                .authorizedToolCodes(List.of())
                .build();

        checkpoint.setAgentSessionId("other-session");
        assertThrows(IllegalArgumentException.class, () -> runtime(agentScopeGateway).resume(
                new AgentResumeRequest(request, checkpoint, 91L, "fp")));
        checkpoint.setAgentSessionId("run-1");
        AgentRunResult result = runtime(agentScopeGateway).resume(
                new AgentResumeRequest(request, checkpoint, 91L, "fp"));

        assertEquals("恢复完成", result.content());
        verify(agentScopeGateway, never()).stream(any(), any(), any());
        verify(agentScopeGateway).resume(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(91L), org.mockito.ArgumentMatchers.eq("fp"));
    }

    private static AgentScopeSpecialistGateway gateway(Flux<ChatStreamChunk> chunks) {
        AgentScopeSpecialistGateway gateway = mock(AgentScopeSpecialistGateway.class);
        when(gateway.stream(any(), any(), any())).thenAnswer(invocation -> Flux.defer(() -> {
            io.agentscope.core.agent.RuntimeContext context = invocation.getArgument(1);
            ExecutionBudget budget = context.get(ExecutionBudget.class);
            ExecutionTools tools = context.get(ExecutionTools.class);
            ExecutionEvents events = context.get(ExecutionEvents.class);
            int callNo = budget.reserveModelCall();
            tools.startModelRound();
            events.modelStarted(callNo, 9L, "test-model", false);
            return chunks.doOnComplete(() -> {
                TokenUsage usage = TokenUsage.of(8, 2, 10);
                budget.completeModelCall(usage);
                events.modelCompleted(callNo, 9L, "test-model", usage, false, null);
            });
        }));
        return gateway;
    }

    private static AgentRuntime runtime(AgentScopeSpecialistGateway gateway) {
        return new AgentRuntime(gateway, mock(AgentScopeSupervisorGateway.class),
                mock(io.agentscope.core.state.AgentStateStore.class));
    }

    private static AgentRunRequest request(RecordingSink sink, long initialSequence) {
        return AgentRunRequest.builder()
                .runId("run-1")
                .agentId(10L)
                .agentVersionId(20L)
                .role(AgentRole.SPECIALIST)
                .modelCommand(command())
                .userId("1")
                .sessionId("run-1")
                .policy(policy(100))
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

    private static ExecutionPolicy policy(int maxTokens) {
        return ExecutionPolicy.builder()
                .maxModelCalls(1)
                .maxToolCalls(0)
                .maxTotalTokens(maxTokens)
                .maxFinalizationTokens(maxTokens)
                .build();
    }

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<ExecutionEvent> events = new ArrayList<>();
        private final List<ChatStreamChunk> chunks = new ArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public long onEvent(ExecutionEvent event) {
            events.add(event);
            return event.getSequenceNo();
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
