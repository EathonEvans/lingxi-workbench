package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityExecutionFacts;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityException;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityResult;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimePlanStore;
import com.lingxi.workbench.agentruntime.plan.SupervisorRunOutcome;
import com.lingxi.workbench.agentruntime.plan.SupervisorRunRequest;
import com.lingxi.workbench.agentruntime.plan.SupervisorRunResult;
import com.lingxi.workbench.agentruntime.planner.PlannerClarification;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerInteractionType;
import com.lingxi.workbench.agentruntime.planner.PlannerPlan;
import com.lingxi.workbench.agentruntime.planner.PlannerRunRequest;
import com.lingxi.workbench.agentruntime.planner.PlannerRunResult;
import com.lingxi.workbench.agentruntime.planner.PlannerStep;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisorRuntimeTest {

    @Test
    void shouldExecuteDirectCapabilityInsideRuntime() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        when(planner.run(any(), any())).thenReturn(planned(direct("research")));
        Fixture fixture = fixture(planner);

        SupervisorRunResult result = fixture.runtime.run(request(fixture, readCapability(),
                invocation -> capabilityResult("研究完成")));

        assertEquals(SupervisorRunOutcome.COMPLETED, result.getOutcome());
        assertEquals("研究完成", result.getContent());
        assertTrue(fixture.sink.types().containsAll(List.of(ExecutionEventType.RUN_STARTED,
                ExecutionEventType.STEP_STARTED, ExecutionEventType.STEP_COMPLETED,
                ExecutionEventType.RUN_COMPLETED)));
    }

    @Test
    void shouldReuseIdenticalWriteStepAndFinalizeWithoutReplayingSideEffect() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        PlannerStep first = step("write1", "提交", "submit");
        PlannerStep second = step("write2", "再次提交", "submit");
        when(planner.run(any(), any())).thenReturn(planned(plan(first, second)));
        Fixture fixture = fixture(planner);
        AtomicInteger invocations = new AtomicInteger();

        SupervisorRunResult result = fixture.runtime.run(request(fixture, writeCapability(), invocation -> {
            invocations.incrementAndGet();
            return capabilityResult("工单T-1");
        }));

        assertEquals(1, invocations.get());
        assertEquals(SupervisorRunOutcome.COMPLETED, result.getOutcome());
        assertEquals("最终回答", result.getContent());
        assertTrue(fixture.sink.types().contains(ExecutionEventType.STEP_REUSED));
        assertEquals(2, fixture.store.invocations.size());
    }

    @Test
    void shouldBoundRetryableFailureWithOneReplan() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        when(planner.run(any(), any())).thenReturn(planned(plan(step("s1", "研究", "research"))),
                planned(direct("research")));
        Fixture fixture = fixture(planner);
        AtomicInteger invocations = new AtomicInteger();

        SupervisorRunResult result = fixture.runtime.run(request(fixture, readCapability(), invocation -> {
            if (invocations.incrementAndGet() == 1) {
                throw new RuntimeCapabilityException("TEMPORARY", "临时失败", true, null);
            }
            return capabilityResult("重规划成功");
        }));

        assertEquals(2, invocations.get());
        assertEquals("重规划成功", result.getContent());
        ArgumentCaptor<PlannerRunRequest> requests = ArgumentCaptor.forClass(PlannerRunRequest.class);
        verify(planner, times(2)).run(requests.capture(), any());
        PlannerRunRequest replan = requests.getAllValues().get(1);
        assertEquals(2, replan.getValidationContext().remainingCapabilityCalls().get("research"));
        assertEquals(3, replan.getValidationContext().getRemainingDelegations());
    }

    @Test
    void shouldFinalizeCompletedStepsWhenCapabilityCallLimitIsReached() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        when(planner.run(any(), any())).thenReturn(planned(plan(
                step("research1", "第一次研究", "research"),
                step("research2", "第二次研究", "research"))));
        Fixture fixture = fixture(planner);
        RuntimeCapability capability = capability("research", RuntimeCapabilityRisk.READ_ONLY, 1);
        AtomicInteger invocations = new AtomicInteger();

        SupervisorRunResult result = fixture.runtime.run(request(fixture, capability, invocation -> {
            invocations.incrementAndGet();
            return capabilityResult("第一步结果");
        }));

        assertEquals(1, invocations.get());
        assertEquals(SupervisorRunOutcome.PARTIAL, result.getOutcome());
        assertEquals("最终回答", result.getContent());
        assertTrue(fixture.sink.types().contains(ExecutionEventType.STEP_FAILED));
        assertTrue(fixture.sink.types().contains(ExecutionEventType.RUN_COMPLETED));
    }

    @Test
    void shouldAdvanceParentSequenceBeforePublishingDelegatedFailure() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        when(planner.run(any(), any())).thenReturn(planned(direct("research")));
        Fixture fixture = fixture(planner);

        assertThrows(RuntimeException.class, () -> fixture.runtime.run(request(fixture, readCapability(), invocation -> {
            invocation.recordExecution(new RuntimeCapabilityExecutionFacts(
                    1, 0, TokenUsage.of(3, 2, 5), 20));
            throw new RuntimeCapabilityException("DOWNSTREAM_FAILED", "下游执行失败", false, null);
        })));

        List<ExecutionEvent> failureEvents = fixture.sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.STEP_FAILED
                        || event.getType() == ExecutionEventType.AGENT_FAILED
                        || event.getType() == ExecutionEventType.RUN_FAILED)
                .toList();
        assertEquals(List.of(21L, 22L, 23L),
                failureEvents.stream().map(ExecutionEvent::getSequenceNo).toList());
    }

    @Test
    void shouldAdvanceParentSequenceBeforePublishingDelegatedCancellation() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        when(planner.run(any(), any())).thenReturn(planned(direct("research")));
        Fixture fixture = fixture(planner);

        assertThrows(AgentRunCancelledException.class,
                () -> fixture.runtime.run(request(fixture, readCapability(), invocation -> {
                    invocation.recordExecution(new RuntimeCapabilityExecutionFacts(
                            1, 0, TokenUsage.of(3, 2, 5), 20));
                    throw new AgentRunCancelledException();
                })));

        List<ExecutionEvent> cancellationEvents = fixture.sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.AGENT_CANCELLED
                        || event.getType() == ExecutionEventType.RUN_CANCELLED)
                .toList();
        assertEquals(List.of(21L, 22L),
                cancellationEvents.stream().map(ExecutionEvent::getSequenceNo).toList());
    }

    @Test
    void shouldRequireReconciliationWhenWriteResultCannotBePersisted() {
        PlannerRuntime planner = mock(PlannerRuntime.class);
        when(planner.run(any(), any())).thenReturn(planned(direct("submit")));
        Fixture fixture = fixture(planner);
        fixture.store.failCompletedWrite = true;

        assertThrows(com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException.class,
                () -> fixture.runtime.run(request(fixture, writeCapability(),
                        invocation -> capabilityResult("工单T-2"))));

        assertEquals(1, fixture.sink.types().stream()
                .filter(type -> type == ExecutionEventType.RUN_RECONCILIATION_PENDING).count());
        assertEquals(0, fixture.sink.types().stream()
                .filter(type -> type == ExecutionEventType.RUN_FAILED).count());
    }

    private static Fixture fixture(PlannerRuntime planner) {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("supervisor").build());
        ChatResponse finalResponse = response("最终回答", 8, 3, 11);
        when(model.call(any(Prompt.class))).thenReturn(finalResponse);
        OpenAiCompatibleChatModelFactory modelFactory = mock(OpenAiCompatibleChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(model);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(estimator.estimate(any(String.class))).thenReturn(20);
        RecordingSink sink = new RecordingSink();
        RecordingStore store = new RecordingStore();
        SupervisorRuntime runtime = new SupervisorRuntime(planner, modelFactory,
                new SpringAiChatClientFactory(ObservationRegistry.NOOP), new RuntimeContextAdvisor(),
                new RuntimeModelAdvisor(), estimator);
        return new Fixture(runtime, config, sink, store);
    }

    private static SupervisorRunRequest request(Fixture fixture, RuntimeCapability capability,
                                                com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityInvoker invoker) {
        return SupervisorRunRequest.builder().runId("run-supervisor").agentId(10L).agentVersionId(20L)
                .plannerSystemPrompt("规划").synthesisSystemPrompt("综合").query("完成任务")
                .history(List.of()).modelConfig(fixture.config)
                .planPolicy(PlanPolicy.builder().maxSteps(4).maxReplans(1).maxProtocolRepairAttempts(1).build())
                .executionPolicy(ExecutionPolicy.builder().maxModelCalls(4).maxToolCalls(4)
                        .maxTotalTokens(200).maxFinalizationTokens(300).build())
                .maxDelegations(4).maxObservationTokens(500).capabilities(List.of(capability))
                .capabilityInvoker(invoker).planStore(fixture.store).eventSink(fixture.sink)
                .initialSequenceNo(0).build();
    }

    private static RuntimeCapabilityResult capabilityResult(String content) {
        return RuntimeCapabilityResult.builder().content(content).build();
    }

    private static RuntimeCapability readCapability() {
        return capability("research", RuntimeCapabilityRisk.READ_ONLY, 3);
    }

    private static RuntimeCapability writeCapability() {
        return capability("submit", RuntimeCapabilityRisk.WRITE, 1);
    }

    private static RuntimeCapability capability(String code, RuntimeCapabilityRisk risk, int maxCalls) {
        return RuntimeCapability.builder().code(code).name(code).type(RuntimeCapabilityType.AGENT)
                .risk(risk).maxCalls(maxCalls).resultTokenBudget(200).build();
    }

    private static PlannerDecision direct(String capabilityCode) {
        return PlannerDecision.builder().action(PlannerDecisionAction.EXECUTE_CAPABILITY)
                .capabilityCode(capabilityCode).build();
    }

    private static PlannerDecision plan(PlannerStep... steps) {
        return PlannerDecision.builder().action(PlannerDecisionAction.EXECUTE_PLAN)
                .plan(PlannerPlan.builder().title("执行计划").steps(List.of(steps)).build()).build();
    }

    private static PlannerStep step(String id, String name, String capabilityCode) {
        return PlannerStep.builder().id(id).name(name).capabilityCode(capabilityCode)
                .dependsOn(List.of()).build();
    }

    private static PlannerRunResult planned(PlannerDecision decision) {
        return new PlannerRunResult(decision, TokenUsage.empty(), 0, 0);
    }

    private static ChatResponse response(String content, int input, int output, int total) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(input);
        when(usage.getCompletionTokens()).thenReturn(output);
        when(usage.getTotalTokens()).thenReturn(total);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
    }

    private record Fixture(SupervisorRuntime runtime, ChatModelRuntimeConfig config,
                           RecordingSink sink, RecordingStore store) {
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

        private List<ExecutionEventType> types() {
            return events.stream().map(ExecutionEvent::getType).toList();
        }
    }

    private static final class RecordingStore implements RuntimePlanStore {
        private final List<AgentInvocationState> invocations = new ArrayList<>();
        private boolean failCompletedWrite;

        @Override
        public void savePlan(String runId, int planNo, PlannerDecision decision) {
        }

        @Override
        public void saveInvocation(AgentInvocationState invocation) {
            if (failCompletedWrite
                    && invocation.getStatus() == com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus.COMPLETED
                    && invocation.getCapability().getRisk().hasSideEffect()) {
                throw new IllegalStateException("database unavailable");
            }
            invocations.add(invocation);
        }
    }
}
