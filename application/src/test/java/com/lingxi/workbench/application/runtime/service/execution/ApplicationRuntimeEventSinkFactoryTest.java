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
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import io.agentscope.core.state.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class ApplicationRuntimeEventSinkFactoryTest {

    @Test
    void shouldPersistSkillEventAndAuditBeforeTimelineProjection() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService, context(publisher));

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
    }

    @Test
    void shouldPersistRuntimeEventBeforeForwardingTimeline() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ChatExecutionContext context = context(publisher);
        RuntimeEventSink sink = sink(persistenceService, context);
        Instant occurredAt = Instant.parse("2026-08-17T14:00:00Z");

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1")
                .sequenceNo(6L)
                .occurredAt(occurredAt)
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED)
                .stage(ExecutionStage.MODEL)
                .agentId(10L)
                .agentVersionId(20L)
                .payload(new ExecutionEventPayload.Model(1, TokenUsage.empty(), false, "模型调用已开始"))
                .build());

        InOrder order = inOrder(persistenceService, publisher);
        order.verify(persistenceService).appendEvent(org.mockito.ArgumentMatchers.any(ExecutionEventCommand.class));
        order.verify(publisher).publishExecution(org.mockito.ArgumentMatchers.any(ChatExecutionTimelineEventBO.class));
        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertEquals(6L, commandCaptor.getValue().getSequenceNo());
        assertEquals(occurredAt, commandCaptor.getValue().getOccurredAt());
        assertEquals(ExecutionEventType.MODEL_STARTED, commandCaptor.getValue().getEventType());
        assertEquals(15, context.getExecutionBudget().remainingModelCalls());
    }

    @Test
    void shouldPersistBeforeProjectingUnifiedToolEventAndReserveBudget() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ChatExecutionContext context = context(publisher, ApplicationRuntimePolicy.builder()
                .maxModelCalls(2)
                .maxToolCalls(1)
                .maxDelegations(0)
                .totalTokenBudget(1000)
                .build());
        RuntimeEventSink sink = sink(persistenceService, context);

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
        assertEquals(0, context.getExecutionBudget().remainingToolCalls());
    }

    @Test
    void shouldForwardTextAndAccumulateUsage() {
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ChatExecutionContext context = context(publisher);
        RuntimeEventSink sink = sink(mock(ExecutionPersistenceService.class), context);
        ChatStreamChunk chunk = new ChatStreamChunk("回答", TokenUsage.of(5, 2, 7));

        sink.onText(chunk);

        verify(publisher).publishText(chunk);
        assertEquals(7, context.getExecutionBudget().tokenUsage().totalTokens());
    }

    @Test
    void shouldProjectAgentScopeTasksWithRevision() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService, context(publisher));
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
    }

    @Test
    void shouldPersistAndProjectTypedStepState() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = sink(persistenceService, context(publisher));
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

    private static ChatExecutionContext context(ChatExecutionEventPublisher publisher) {
        return context(publisher, ApplicationRuntimePolicy.defaults());
    }

    private static ChatExecutionContext context(ChatExecutionEventPublisher publisher,
                                                ApplicationRuntimePolicy runtimePolicy) {
        return ChatExecutionContext.builder()
                .executionId("run-1")
                .runtimeVariables(mock(RuntimeVariables.class))
                .turn(new ChatTurnBO(1L, 2L, 3L, 1))
                .eventPublisher(publisher)
                .executionBudget(ApplicationExecutionBudget.fromRuntimePolicy(runtimePolicy))
                .build();
    }

    private static RuntimeEventSink sink(ExecutionPersistenceService persistenceService,
                                         ChatExecutionContext context) {
        return new ApplicationRuntimeEventSinkFactory(persistenceService).create(context.getExecutionBudget(),
                context.getEventPublisher());
    }
}
