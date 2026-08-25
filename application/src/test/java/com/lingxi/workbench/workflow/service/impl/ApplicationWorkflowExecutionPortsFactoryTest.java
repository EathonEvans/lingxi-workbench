package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.agent.ChatAgentNodeInvoker;
import com.lingxi.workbench.application.runtime.service.routing.WorkflowIntentClassifierNodeInvoker;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;
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
        IntentClassifierNodeInvocation intentInvocation = IntentClassifierNodeInvocation.builder()
                .executionId("execution-1")
                .runId("workflow-run-1")
                .workflowVersionId(11L)
                .nodeId("intent")
                .intentTreeId(30L)
                .intentTreeVersionId(31L)
                .query("问题")
                .build();
        IntentClassifierNodeResult intentResult = mock(IntentClassifierNodeResult.class);
        when(intentClassifierNodeInvoker.invoke(intentInvocation, executionContext)).thenReturn(intentResult);

        WorkflowExecutionPorts ports = new ApplicationWorkflowExecutionPortsFactory(
                runService, agentNodeInvoker, intentClassifierNodeInvoker).create(configureOwnership(executionContext));

        assertThat(ports.agentNodeInvoker().invoke(invocation)).containsEntry("answer", "回答");
        assertSame(intentResult, ports.intentClassifierNodeInvoker().invoke(intentInvocation));
        verify(agentNodeInvoker).invoke(invocation, executionContext);
        verify(intentClassifierNodeInvoker).invoke(intentInvocation, executionContext);
    }

    @Test
    void shouldPublishNodeStartedTimelineEvent() {
        WorkflowRunService runService = mock(WorkflowRunService.class);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ChatExecutionEventPublisher publisher = mock(ChatExecutionEventPublisher.class);
        when(executionContext.getEventPublisher()).thenReturn(publisher);
        WorkflowRunListener listener = new ApplicationWorkflowExecutionPortsFactory(
                runService, mock(ChatAgentNodeInvoker.class), mock(WorkflowIntentClassifierNodeInvoker.class))
                .create(configureOwnership(executionContext)).runListener();
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
    void shouldPersistWorkflowStartWithExplicitOwnershipCommand() {
        WorkflowRunService runService = mock(WorkflowRunService.class);
        ChatExecutionContext executionContext = configureOwnership(mock(ChatExecutionContext.class));
        WorkflowRunListener listener = new ApplicationWorkflowExecutionPortsFactory(
                runService, mock(ChatAgentNodeInvoker.class), mock(WorkflowIntentClassifierNodeInvoker.class))
                .create(executionContext).runListener();

        WorkflowRunContext context = runContext();
        listener.onRunStarted(context);

        ArgumentCaptor<com.lingxi.workbench.application.runtime.model.WorkflowRunStartCommand> commandCaptor =
                ArgumentCaptor.forClass(com.lingxi.workbench.application.runtime.model.WorkflowRunStartCommand.class);
        verify(runService).start(org.mockito.ArgumentMatchers.eq(context), commandCaptor.capture());
        assertThat(commandCaptor.getValue()).satisfies(command -> {
            assertThat(command.getAssistantMessageId()).isEqualTo(4L);
            assertThat(command.getConversationId()).isEqualTo(2L);
            assertThat(command.getUserId()).isEqualTo(5L);
            assertThat(command.getApplicationId()).isEqualTo(6L);
            assertThat(command.getApplicationVersionId()).isEqualTo(7L);
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
                .create(configureOwnership(executionContext)).runListener();
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
                .definition(new WorkflowDefinition(2, "test", List.of(startNode()), List.of()))
                .request(new WorkflowExecutionRequest("execution-1", 10L, 11L, Map.of("query", "测试")))
                .ports(new WorkflowExecutionPorts(invocation -> Map.of(), invocation -> mock(IntentClassifierNodeResult.class), null))
                .startedAt(Instant.parse("2026-08-09T06:00:00Z"))
                .build();
    }

    private static WorkflowNodeDefinition startNode() {
        return new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig());
    }

    private static ChatExecutionContext configureOwnership(ChatExecutionContext executionContext) {
        ConversationContext conversation = mock(ConversationContext.class);
        AppRuntimeSnapshot application = mock(AppRuntimeSnapshot.class);
        when(executionContext.getConversationContext()).thenReturn(conversation);
        when(executionContext.getTurn()).thenReturn(new ChatTurnBO(2L, 3L, 4L, 1));
        when(conversation.getLoginUser()).thenReturn(new LoginUser(5L, "tester", "测试用户", null));
        when(conversation.getApplicationSnapshot()).thenReturn(application);
        when(application.applicationId()).thenReturn(6L);
        when(application.versionId()).thenReturn(7L);
        return executionContext;
    }
}
