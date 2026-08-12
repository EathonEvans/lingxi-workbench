package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionContextRegistry;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowRunService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRunListenerImplTest {

    @Test
    void shouldPublishNodeStartedTimelineEvent() {
        WorkflowRunService runService = mock(WorkflowRunService.class);
        ChatExecutionContextRegistry registry = mock(ChatExecutionContextRegistry.class);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        when(registry.require("execution-1")).thenReturn(executionContext);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        WorkflowRunListenerImpl listener = new WorkflowRunListenerImpl(runService, registry);
        WorkflowRunContext context = runContext();
        WorkflowNodeDefinition node = startNode();
        Instant startedAt = Instant.parse("2026-08-09T06:00:00Z");

        listener.onNodeStarted(context, node, startedAt);

        verify(runService).startNode(context, node, startedAt);
        ArgumentCaptor<ChatExecutionTimelineEventBO> eventCaptor = ArgumentCaptor.forClass(ChatExecutionTimelineEventBO.class);
        verify(publisher).publishExecution(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.getRunId()).isEqualTo("workflow-run-1");
            assertThat(event.getEventType()).isEqualTo(ExecutionEventType.STEP_STARTED);
            assertThat(event.getStepStatus()).isEqualTo(ExecutionStepStatus.RUNNING);
            assertThat(event.getStepId()).isEqualTo("start");
        });
    }

    @Test
    void shouldKeepPersistedNodeStateWhenTimelinePublishingFails() {
        WorkflowRunService runService = mock(WorkflowRunService.class);
        ChatExecutionContextRegistry registry = mock(ChatExecutionContextRegistry.class);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        when(registry.require("execution-1")).thenReturn(executionContext);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        doThrow(new IllegalStateException("SSE连接已关闭")).when(publisher).publishExecution(any(ChatExecutionTimelineEventBO.class));
        WorkflowRunListenerImpl listener = new WorkflowRunListenerImpl(runService, registry);
        WorkflowRunContext context = runContext();
        WorkflowNodeDefinition node = startNode();
        Instant startedAt = Instant.parse("2026-08-09T06:00:00Z");

        assertDoesNotThrow(() -> listener.onNodeStarted(context, node, startedAt));

        verify(runService).startNode(context, node, startedAt);
        verify(publisher).publishExecution(any(ChatExecutionTimelineEventBO.class));
    }

    private static WorkflowRunContext runContext() {
        return WorkflowRunContext.builder()
                .runId("workflow-run-1")
                .definition(new WorkflowDefinition(1, "test", List.of(startNode()), List.of()))
                .request(new WorkflowExecutionRequest("execution-1", 10L, 11L, Map.of("query", "测试")))
                .startedAt(Instant.parse("2026-08-09T06:00:00Z"))
                .build();
    }

    private static WorkflowNodeDefinition startNode() {
        return new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig());
    }
}
