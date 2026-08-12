package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutorRuntimeContext;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.plan.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.plan.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannerRuntimeCoordinatorImplTest {

    @Test
    void shouldPersistAndPublishTerminalEventWhenInitialPlanningFails() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        PlannerService plannerService = mock(PlannerService.class);
        PlanExecutionService planExecutionService = mock(PlanExecutionService.class);
        RecordingPublisher publisher = new RecordingPublisher();
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        when(executionContext.getRuntimePolicy()).thenReturn(ApplicationRuntimePolicy.builder()
                .maxModelCalls(6).build());
        when(plannerService.decide(any())).thenThrow(
                new BusinessException(HttpStatusEnum.CONFLICT, "Planner输出无效"));
        PlannerRuntimeCoordinatorImpl coordinator = new PlannerRuntimeCoordinatorImpl(
                persistenceService, plannerService, planExecutionService, new ExecutionFailureClassifier());
        ConversationContext context = mock(ConversationContext.class);
        when(context.getLoginUser()).thenReturn(new LoginUser(7L, "tester", "测试用户", null));
        PlannerRuntimeRequest request = PlannerRuntimeRequest.builder()
                .runId("run-1")
                .conversationContext(context)
                .executionContext(executionContext)
                .planExecutor(planExecutor())
                .build();

        assertThrows(BusinessException.class, () -> coordinator.execute(request));

        ArgumentCaptor<ExecutionEventCommand> eventCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService, org.mockito.Mockito.times(2)).appendEvent(eventCaptor.capture());
        assertEquals(List.of(ExecutionEventType.PLANNING, ExecutionEventType.RUN_FAILED),
                eventCaptor.getAllValues().stream().map(ExecutionEventCommand::getEventType).toList());
        assertEquals(List.of(ExecutionEventType.PLANNING, ExecutionEventType.RUN_FAILED),
                publisher.events.stream().map(ChatExecutionTimelineEventBO::getEventType).toList());
        assertEquals("Planner输出无效", publisher.events.getLast().getMessage());
        verify(planExecutionService, never()).execute(any());
    }

    @Test
    void shouldBypassPlannerWhenContextPreparationReachedBudgetBoundary() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        PlannerService plannerService = mock(PlannerService.class);
        PlanExecutionService planExecutionService = mock(PlanExecutionService.class);
        RecordingPublisher publisher = new RecordingPublisher();
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        when(executionContext.shouldFinalize()).thenReturn(true);
        when(planExecutionService.execute(any())).thenReturn(
                new PlanExecutionResult("请缩小问题范围", null, List.of()));
        PlannerRuntimeCoordinatorImpl coordinator = new PlannerRuntimeCoordinatorImpl(
                persistenceService, plannerService, planExecutionService, new ExecutionFailureClassifier());
        ConversationContext context = mock(ConversationContext.class);
        when(context.getLoginUser()).thenReturn(new LoginUser(7L, "tester", "测试用户", null));
        PlannerRuntimeRequest request = PlannerRuntimeRequest.builder()
                .runId("run-budget")
                .conversationContext(context)
                .executionContext(executionContext)
                .planExecutor(planExecutor())
                .build();

        PlanExecutionResult result = coordinator.execute(request);

        assertEquals("请缩小问题范围", result.content());
        ArgumentCaptor<PlanExecutionRequest> requestCaptor = ArgumentCaptor.forClass(PlanExecutionRequest.class);
        verify(planExecutionService).execute(requestCaptor.capture());
        assertEquals(ExecutionDecisionAction.CLARIFY, requestCaptor.getValue().getInitialDecision().getAction());
        verify(plannerService, never()).decide(any());
    }

    private static PlanExecutorRuntimeContext planExecutor() {
        return PlanExecutorRuntimeContext.builder()
                .capability(mock(ApplicationCapabilityDefinition.class))
                .agent(mock(AgentRuntimeSnapshot.class))
                .build();
    }

    private static final class RecordingPublisher implements ChatExecutionEventPublisher {
        private final List<ChatExecutionTimelineEventBO> events = new ArrayList<>();

        @Override
        public void publishText(ChatStreamChunk chunk) {
        }

        @Override
        public void publishTool(AgentToolExecutionEvent event) {
        }

        @Override
        public void publishExecution(ChatExecutionTimelineEventBO event) {
            events.add(event);
        }
    }
}
