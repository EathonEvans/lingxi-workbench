package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.agent.ChatAgentNodeInvoker;
import com.lingxi.workbench.application.runtime.service.routing.WorkflowIntentClassifierNodeInvoker;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionPorts;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowRunService;
import com.lingxi.workbench.workflow.service.WorkflowRunListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationWorkflowExecutionPortsFactoryTest {

    @Test
    void shouldBindAgentInvocationAndIntentClassifierToCurrentExecution() {
        WorkflowRunService runService = mock(WorkflowRunService.class);
        ChatAgentNodeInvoker agentNodeInvoker = mock(ChatAgentNodeInvoker.class);
        WorkflowIntentClassifierNodeInvoker intentClassifierNodeInvoker = mock(WorkflowIntentClassifierNodeInvoker.class);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        AgentNodeInvocation invocation = AgentNodeInvocation.builder()
                .executionId("execution-1")
                .workflowVersionId(11L)
                .nodeId("agent")
                .agentId(20L)
                .agentVersionId(21L)
                .inputs(Map.of("query", "问题"))
                .build();
        when(agentNodeInvoker.invoke(invocation, executionContext)).thenReturn(Map.of("answer", "回答"));

        WorkflowExecutionPorts ports = new ApplicationWorkflowExecutionPortsFactory(
                runService, agentNodeInvoker, intentClassifierNodeInvoker).create(executionContext);

        assertThat(ports.agentNodeInvoker().invoke(invocation)).containsEntry("answer", "回答");
        assertSame(intentClassifierNodeInvoker, ports.intentClassifierNodeInvoker());
        verify(agentNodeInvoker).invoke(invocation, executionContext);
    }

    @Test
    void shouldPublishNodeStartedTimelineEvent() {
        WorkflowRunService runService = mock(WorkflowRunService.class);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        WorkflowRunListener listener = new ApplicationWorkflowExecutionPortsFactory(
                runService, mock(ChatAgentNodeInvoker.class), mock(WorkflowIntentClassifierNodeInvoker.class))
                .create(executionContext).runListener();
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
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        doThrow(new IllegalStateException("SSE连接已关闭")).when(publisher).publishExecution(any(ChatExecutionTimelineEventBO.class));
        WorkflowRunListener listener = new ApplicationWorkflowExecutionPortsFactory(
                runService, mock(ChatAgentNodeInvoker.class), mock(WorkflowIntentClassifierNodeInvoker.class))
                .create(executionContext).runListener();
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
                .ports(new WorkflowExecutionPorts(invocation -> Map.of(), invocation -> Map.of(), null))
                .startedAt(Instant.parse("2026-08-09T06:00:00Z"))
                .build();
    }

    private static WorkflowNodeDefinition startNode() {
        return new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig());
    }
}
