package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionStage;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerPlan;
import com.lingxi.workbench.agentruntime.planner.PlannerStep;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationRuntimeEventSinkFactoryTest {

    @Test
    void shouldPersistRuntimeEventBeforeForwardingTimeline() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ChatExecutionContext context = context(publisher);
        RuntimeEventSink sink = new ApplicationRuntimeEventSinkFactory(persistenceService)
                .create(context);
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
        assertEquals(15, context.remainingModelCalls());
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
                .timeoutSeconds(60)
                .build());
        RuntimeEventSink sink = new ApplicationRuntimeEventSinkFactory(persistenceService).create(context);

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
        assertEquals(0, context.remainingToolCalls());
    }

    @Test
    void shouldForwardTextAndAccumulateUsage() {
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        ChatExecutionContext context = context(publisher);
        RuntimeEventSink sink = new ApplicationRuntimeEventSinkFactory(mock(ExecutionPersistenceService.class))
                .create(context);
        ChatStreamChunk chunk = new ChatStreamChunk("回答", TokenUsage.of(5, 2, 7));

        sink.onText(chunk);

        verify(publisher).publishText(chunk);
        assertEquals(7, context.tokenUsage().totalTokens());
    }

    @Test
    void shouldProjectTypedPlanWithStepsAndPlanNumber() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        RuntimeEventSink sink = new ApplicationRuntimeEventSinkFactory(persistenceService)
                .create(context(publisher));
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_PLAN)
                .plan(PlannerPlan.builder()
                        .title("调研计划")
                        .steps(List.of(PlannerStep.builder()
                                .id("research")
                                .name("收集资料")
                                .capabilityCode("research_agent")
                                .dependsOn(List.of())
                                .build()))
                        .build())
                .build();

        sink.onEvent(ExecutionEvent.builder()
                .runId("run-1")
                .sequenceNo(9L)
                .occurredAt(Instant.parse("2026-08-17T14:00:02Z"))
                .type(com.lingxi.workbench.agentruntime.event.ExecutionEventType.PLAN_CREATED)
                .stage(ExecutionStage.PLAN)
                .agentId(10L)
                .agentVersionId(20L)
                .payload(new ExecutionEventPayload.Plan(2, decision, "规划决策已生成"))
                .build());

        ArgumentCaptor<ExecutionEventCommand> commandCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService).appendEvent(commandCaptor.capture());
        assertEquals(2, commandCaptor.getValue().getPlanNo());
        ArgumentCaptor<ChatExecutionTimelineEventBO> timelineCaptor =
                ArgumentCaptor.forClass(ChatExecutionTimelineEventBO.class);
        verify(publisher).publishExecution(timelineCaptor.capture());
        assertEquals(2, timelineCaptor.getValue().getPlanNo());
        assertEquals("调研计划", timelineCaptor.getValue().getDisplayName());
        assertEquals("research", timelineCaptor.getValue().steps().getFirst().stepId());
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
                .runtimePolicy(runtimePolicy)
                .build();
    }
}
