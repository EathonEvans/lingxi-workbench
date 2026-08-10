package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.InteractionSessionService;
import com.lingxi.workbench.application.runtime.service.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
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
        when(plannerService.decide(any())).thenThrow(
                new BusinessException(HttpStatusEnum.CONFLICT, "Planner输出无效"));
        PlannerRuntimeCoordinatorImpl coordinator = new PlannerRuntimeCoordinatorImpl(
                mock(InteractionSessionService.class), persistenceService, plannerService, planExecutionService);
        ConversationContext context = mock(ConversationContext.class);
        PlannerPolicy policy = PlannerPolicy.builder().enabled(true).maxModelCalls(6).build();
        ApplicationDefinition definition = mock(ApplicationDefinition.class);
        when(definition.plannerPolicy()).thenReturn(policy);
        AppRuntimeSnapshot snapshot = mock(AppRuntimeSnapshot.class);
        when(snapshot.definition()).thenReturn(definition);
        when(context.getApplicationSnapshot()).thenReturn(snapshot);
        PlannerRuntimeRequest request = PlannerRuntimeRequest.builder()
                .runId("run-1")
                .conversationContext(context)
                .executionContext(executionContext)
                .startedAt(Instant.now())
                .build();

        assertThrows(BusinessException.class, () -> coordinator.execute(request));

        ArgumentCaptor<ExecutionEventCommand> eventCaptor = ArgumentCaptor.forClass(ExecutionEventCommand.class);
        verify(persistenceService, org.mockito.Mockito.times(2)).appendEvent(eventCaptor.capture());
        assertEquals(List.of(ExecutionEventType.PLANNING, ExecutionEventType.RUN_FAILED),
                eventCaptor.getAllValues().stream().map(ExecutionEventCommand::getEventType).toList());
        assertEquals(List.of(ExecutionEventType.PLANNING, ExecutionEventType.RUN_FAILED),
                publisher.events.stream().map(ChatExecutionTimelineEventBO::getEventType).toList());
        verify(planExecutionService, never()).execute(any());
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
