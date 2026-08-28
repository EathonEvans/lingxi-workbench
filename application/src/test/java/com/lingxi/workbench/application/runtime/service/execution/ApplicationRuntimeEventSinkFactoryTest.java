package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionStage;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus;
import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStep;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import io.agentscope.core.state.Task;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

class ApplicationRuntimeEventSinkFactoryTest {

    @Test
    void shouldPersistSkillEventAndProjectUserFacingMessage() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()), publisher);

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1").sequenceNo(5L).occurredAt(Instant.parse("2026-08-17T14:00:00Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.SKILL_ACTIVATED)
                .stage(ExecutionStage.SKILL).agentId(10L).agentVersionId(20L)
                .payload(ExecutionEventPayload.Skill.builder().skillId(1L).skillVersionId(11L)
                        .versionNo(1).skillCode("fact_check").checksum("checksum")
                        .reason(SkillActivationReason.MODEL_ROUTED).effectiveToolCodes(List.of("search"))
                        .message("Skill已激活：事实核验").build())
                .build());

        InOrder order = inOrder(persistenceService, publisher);
        order.verify(persistenceService).appendSkillActivationEvent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        order.verify(publisher).publishExecution(org.mockito.ArgumentMatchers.any());
        verify(persistenceService, never()).appendEvent(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<ChatExecutionTimelineEventBO> timelineCaptor =
                ArgumentCaptor.forClass(ChatExecutionTimelineEventBO.class);
        verify(publisher).publishExecution(timelineCaptor.capture());
        assertEquals("已准备相关专业能力", timelineCaptor.getValue().getMessage());
    }

    @Test
    void shouldPersistRuntimeEventBeforeForwardingTimeline() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults());
        RuntimeEventSink sink = sink(persistenceService, budget, publisher);
        Instant occurredAt = Instant.parse("2026-08-17T14:00:00Z");

        long persistedSequence = sink.onEvent(ExecutionEvent.builder()
                .runId("run-1")
                .sequenceNo(6L)
                .occurredAt(occurredAt)
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED)
                .stage(ExecutionStage.MODEL)
                .agentId(10L)
                .agentVersionId(20L)
                .payload(new ExecutionEventPayload.Model(1, 9L, "test-model", TokenUsage.empty(), false, null,
                        "模型调用已开始"))
                .build());

        InOrder order = inOrder(persistenceService, publisher);
        order.verify(persistenceService).appendEvent(org.mockito.ArgumentMatchers.any(ExecutionEventCommand.class));
        order.verify(publisher).publishExecution(org.mockito.ArgumentMatchers.any(ChatExecutionTimelineEventBO.class));
        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertNull(commandCaptor.getValue().getSequenceNo());
        assertEquals(1L, persistedSequence);
        assertEquals(occurredAt, commandCaptor.getValue().getOccurredAt());
        assertEquals(ExecutionEventType.MODEL_STARTED, commandCaptor.getValue().getEventType());
        assertEquals("正在分析问题", commandCaptor.getValue().getMessage());
        assertEquals(15, budget.remainingModelCalls());
    }

    @Test
    void shouldPersistBeforeProjectingUnifiedToolEventAndReserveBudget() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.builder()
                .maxModelCalls(2)
                .maxToolCalls(1)
                .maxDelegations(0)
                .totalTokenBudget(1000)
                .build());
        RuntimeEventSink sink = sink(persistenceService, budget, publisher);

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1")
                .sequenceNo(7L)
                .occurredAt(Instant.parse("2026-08-17T14:00:01Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_STARTED)
                .stage(ExecutionStage.TOOL)
                .agentId(10L)
                .agentVersionId(20L)
                .invocationId("invocation-1")
                .payload(new ExecutionEventPayload.Tool("search_docs", "检索文档", null, null))
                .build());

        InOrder order = inOrder(persistenceService, publisher);
        order.verify(persistenceService).appendEvent(org.mockito.ArgumentMatchers.any(ExecutionEventCommand.class));
        order.verify(publisher).publishExecution(org.mockito.ArgumentMatchers.any(ChatExecutionTimelineEventBO.class));
        order.verify(publisher).publishTool(org.mockito.ArgumentMatchers.any(AgentToolExecutionEvent.class));
        ArgumentCaptor<AgentToolExecutionEvent> toolCaptor = ArgumentCaptor.forClass(AgentToolExecutionEvent.class);
        verify(publisher).publishTool(toolCaptor.capture());
        assertEquals("invocation-1", toolCaptor.getValue().getInvocationId());
        assertEquals(AgentToolExecutionStatus.RUNNING, toolCaptor.getValue().getStatus());
        assertEquals(0, budget.remainingToolCalls());
    }

    @Test
    void shouldProjectAgentCapabilityAsScopedSubAgentEvent() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2).maxToolCalls(1).maxDelegations(1).totalTokenBudget(1000).build()),
                publisher);

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1").sequenceNo(8L).occurredAt(Instant.parse("2026-08-17T14:00:01Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_STARTED)
                .stage(ExecutionStage.TOOL).agentId(10L).agentVersionId(20L).invocationId("delegation-1")
                .payload(ExecutionEventPayload.Tool.builder()
                        .toolCode("career_coach").displayName("个人求职教练")
                        .delegatedAgentId(30L).taskRevision(2).stepId("analyze-role").build())
                .build());

        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertEquals(2, commandCaptor.getValue().getPlanNo());
        assertEquals("analyze-role", commandCaptor.getValue().getStepId());
        ArgumentCaptor<AgentToolExecutionEvent> toolCaptor = ArgumentCaptor.forClass(AgentToolExecutionEvent.class);
        verify(publisher).publishTool(toolCaptor.capture());
        assertEquals("delegate_agent_30", toolCaptor.getValue().getToolCode());
        assertEquals(2, toolCaptor.getValue().getPlanNo());
        assertEquals("analyze-role", toolCaptor.getValue().getStepId());
    }

    @Test
    void shouldForwardTextAndAccumulateUsage() {
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults());
        RuntimeEventSink sink = sink(mock(ExecutionPersistenceService.class), budget, publisher);
        ChatStreamChunk chunk = new ChatStreamChunk("回答", TokenUsage.of(5, 2, 7));

        sink.onText(chunk);

        verify(publisher).publishText(chunk);
        assertEquals(7, budget.tokenUsage().totalTokens());
    }

    @Test
    void shouldProjectAgentScopeTasksWithRevision() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()), publisher);
        Task task = Task.builder().id("research").subject("收集资料").description("收集资料")
                .state(Task.State.PENDING).metadata(java.util.Map.of("capabilityCode", "research_agent"))
                .build();

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1")
                .sequenceNo(9L)
                .occurredAt(Instant.parse("2026-08-17T14:00:02Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.PLAN_CREATED)
                .stage(ExecutionStage.PLAN)
                .agentId(10L)
                .agentVersionId(20L)
                .payload(new ExecutionEventPayload.Plan(2, List.of(task), "任务计划已更新"))
                .build());

        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertEquals(2, commandCaptor.getValue().getPlanNo());
        ArgumentCaptor<ChatExecutionTimelineEventBO> timelineCaptor =
                ArgumentCaptor.forClass(ChatExecutionTimelineEventBO.class);
        verify(publisher).publishExecution(timelineCaptor.capture());
        assertEquals(2, timelineCaptor.getValue().getPlanNo());
        assertEquals("任务计划已更新", timelineCaptor.getValue().getMessage());
        assertEquals("research", timelineCaptor.getValue().steps().getFirst().stepId());
        assertEquals(ExecutionStepStatus.PENDING, timelineCaptor.getValue().steps().getFirst().status());
    }

    @Test
    void shouldPersistAndProjectTypedStepState() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()), publisher);
        RuntimeTaskStep step = RuntimeTaskStep.builder().id("research").name("收集资料")
                .capabilityCode("research_agent").build();

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1")
                .sequenceNo(10L)
                .occurredAt(Instant.parse("2026-08-17T14:00:03Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.STEP_COMPLETED)
                .stage(ExecutionStage.PLAN)
                .agentId(10L)
                .agentVersionId(20L)
                .payload(new ExecutionEventPayload.Step(
                        2, step, AgentInvocationStatus.COMPLETED, null, "步骤已完成"))
                .build());

        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertEquals("research", commandCaptor.getValue().getStepId());
        assertEquals(ExecutionStepStatus.COMPLETED, commandCaptor.getValue().getStepStatus());
        ArgumentCaptor<ChatExecutionTimelineEventBO> timelineCaptor =
                ArgumentCaptor.forClass(ChatExecutionTimelineEventBO.class);
        verify(publisher).publishExecution(timelineCaptor.capture());
        assertEquals("research", timelineCaptor.getValue().getStepId());
        assertEquals(ExecutionStepStatus.COMPLETED, timelineCaptor.getValue().getStepStatus());
        assertEquals("收集资料", timelineCaptor.getValue().getDisplayName());
    }

    @Test
    void shouldPersistCoordinationWithoutExposingInternalEventToChat() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ApplicationExecutionBudget budget = ApplicationExecutionBudget.fromRuntimePolicy(
                ApplicationRuntimePolicy.defaults());
        RuntimeEventSink sink = sink(persistenceService, budget, publisher);

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1").sequenceNo(11L).occurredAt(Instant.parse("2026-08-17T14:00:04Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.COORDINATION_STARTED)
                .stage(ExecutionStage.COORDINATION).agentId(10L).agentVersionId(20L)
                .invocationId("coordination-1")
                .payload(new ExecutionEventPayload.Coordination("wait_async_results", "结果等待", null, null))
                .build());

        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertEquals(ExecutionEventType.COORDINATION_STARTED, commandCaptor.getValue().getEventType());
        assertEquals(16, budget.remainingModelCalls());
        assertEquals(20, budget.remainingToolCalls());
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldPairCoordinationEventsIntoRuntimeObservation() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        List<Observation.Context> stopped = new ArrayList<>();
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return "lingxi.ai.runtime.phase".equals(context.getName());
            }
        });
        RuntimeEventSink sink = sink(persistenceService,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()), publisher,
                observationRegistry);

        sink.onEvent(coordinationEvent(
                com.lingxi.workbench.agentruntime.event.ExecutionEventType.COORDINATION_STARTED, null));
        sink.onEvent(coordinationEvent(
                com.lingxi.workbench.agentruntime.event.ExecutionEventType.COORDINATION_COMPLETED, 12L));

        assertEquals(1, stopped.size());
        Observation.Context context = stopped.getFirst();
        assertEquals("COORDINATION", context.getLowCardinalityKeyValue("lingxi.runtime.phase").getValue());
        assertEquals("wait_async_results",
                context.getLowCardinalityKeyValue("lingxi.runtime.operation").getValue());
        assertNull(context.getError());
    }

    @Test
    void shouldAttachFirstResponseLatencyToModelObservation() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        List<Observation.Context> stopped = new ArrayList<>();
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return "lingxi.ai.runtime.phase".equals(context.getName());
            }
        });
        RuntimeEventSink sink = sink(persistenceService,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()), publisher,
                observationRegistry);

        sink.onEvent(modelEvent(com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED,
                null));
        sink.onEvent(modelEvent(com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_COMPLETED,
                730L, TokenUsage.of(1200, 240, 1440)));

        assertEquals(1, stopped.size());
        assertEquals("730", stopped.getFirst()
                .getHighCardinalityKeyValue("lingxi.runtime.model.first_response_ms").getValue());
        assertEquals("1200", stopped.getFirst()
                .getHighCardinalityKeyValue("lingxi.runtime.model.input_tokens").getValue());
        assertEquals("240", stopped.getFirst()
                .getHighCardinalityKeyValue("lingxi.runtime.model.output_tokens").getValue());
        assertEquals("9", stopped.getFirst()
                .getHighCardinalityKeyValue("lingxi.runtime.model.instance_id").getValue());
        assertEquals("test-model", stopped.getFirst()
                .getHighCardinalityKeyValue("lingxi.runtime.model.code").getValue());
    }

    private static RuntimeEventSink sink(ExecutionPersistenceService persistenceService,
                                         ApplicationExecutionBudget budget,
                                         ChatExecutionEventPublisher publisher) {
        return sink(persistenceService, budget, publisher, ObservationRegistry.NOOP);
    }

    private static RuntimeEventSink sink(ExecutionPersistenceService persistenceService,
                                         ApplicationExecutionBudget budget,
                                         ChatExecutionEventPublisher publisher,
                                         ObservationRegistry observationRegistry) {
        org.mockito.Mockito.when(persistenceService.appendEvent(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        org.mockito.Mockito.when(persistenceService.appendSkillActivationEvent(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        return new ApplicationRuntimeEventSinkFactory(persistenceService, observationRegistry)
                .create(budget, publisher);
    }

    private static ExecutionEvent coordinationEvent(
            com.lingxi.workbench.agentruntime.event.ExecutionEventType eventType, Long durationMillis) {
        return ExecutionEvent.builder()
                .runId("run-observation-1").sequenceNo(12L).occurredAt(Instant.parse("2026-08-17T14:00:05Z"))
                .type(eventType).stage(ExecutionStage.COORDINATION).agentId(10L).agentVersionId(20L)
                .invocationId("coordination-observation-1")
                .payload(new ExecutionEventPayload.Coordination(
                        "wait_async_results", "结果等待", durationMillis, null))
                .build();
    }

    private static ExecutionEvent modelEvent(
            com.lingxi.workbench.agentruntime.event.ExecutionEventType eventType, Long firstResponseMillis) {
        return modelEvent(eventType, firstResponseMillis, TokenUsage.empty());
    }

    private static ExecutionEvent modelEvent(
            com.lingxi.workbench.agentruntime.event.ExecutionEventType eventType, Long firstResponseMillis,
            TokenUsage usage) {
        return ExecutionEvent.builder()
                .runId("run-observation-1").sequenceNo(13L).occurredAt(Instant.parse("2026-08-17T14:00:05Z"))
                .type(eventType).stage(ExecutionStage.MODEL).agentId(10L).agentVersionId(20L)
                .payload(new ExecutionEventPayload.Model(1, 9L, "test-model", usage, false,
                        firstResponseMillis, "模型调用"))
                .build();
    }

    private static ExecutionEventEntity persisted(ExecutionEventCommand command) {
        ExecutionEventEntity event = new ExecutionEventEntity();
        event.setRunId(command.getRunId());
        event.setSequenceNo(java.util.Objects.requireNonNullElse(command.getSequenceNo(), 1L));
        event.setEventType(command.getEventType());
        return event;
    }
}
