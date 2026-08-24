package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunEventType;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunStatus;
import com.lingxi.workbench.workflow.common.enums.WorkflowNodeRunStatus;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionPorts;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeStatus;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.model.entity.WorkflowNodeAttemptEntity;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEntity;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEventEntity;
import com.lingxi.workbench.workflow.repo.WorkflowNodeAttemptRepo;
import com.lingxi.workbench.workflow.repo.WorkflowRunEventRepo;
import com.lingxi.workbench.workflow.repo.WorkflowRunRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRunServiceImplTest {

    private final WorkflowRunRepo runRepo = mock(WorkflowRunRepo.class);
    private final WorkflowNodeAttemptRepo attemptRepo = mock(WorkflowNodeAttemptRepo.class);
    private final WorkflowRunEventRepo eventRepo = mock(WorkflowRunEventRepo.class);
    private final WorkflowRunServiceImpl service = new WorkflowRunServiceImpl(runRepo, attemptRepo, eventRepo);

    @Test
    void shouldPersistRunOwnershipAndLockedVersions() {
        WorkflowRunContext runContext = runContext();
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ConversationContext conversation = mock(ConversationContext.class);
        AppRuntimeSnapshot app = mock(AppRuntimeSnapshot.class);
        when(executionContext.getConversationContext()).thenReturn(conversation);
        when(executionContext.getTurn()).thenReturn(new ChatTurnBO(3L, 4L, 5L, 1));
        when(conversation.getApplicationSnapshot()).thenReturn(app);
        when(conversation.getLoginUser()).thenReturn(new LoginUser(6L, "tester", "测试用户", null));
        when(app.applicationId()).thenReturn(7L);
        when(app.versionId()).thenReturn(8L);

        service.start(runContext, executionContext);

        ArgumentCaptor<WorkflowRunEntity> runCaptor = ArgumentCaptor.forClass(WorkflowRunEntity.class);
        verify(runRepo).insert(runCaptor.capture());
        assertThat(runCaptor.getValue()).satisfies(run -> {
            assertThat(run.getRunId()).isEqualTo("workflow-run-1");
            assertThat(run.getExecutionId()).isEqualTo("execution-1");
            assertThat(run.getAssistantMessageId()).isEqualTo(5L);
            assertThat(run.getApplicationVersionId()).isEqualTo(8L);
            assertThat(run.getWorkflowId()).isEqualTo(10L);
            assertThat(run.getWorkflowVersionId()).isEqualTo(11L);
            assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.RUNNING);
        });
        ArgumentCaptor<WorkflowRunEventEntity> eventCaptor = ArgumentCaptor.forClass(WorkflowRunEventEntity.class);
        verify(eventRepo).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(WorkflowRunEventType.RUN_STARTED);
    }

    @Test
    void shouldPersistFailedNodeWithoutInputsOrOutputs() {
        WorkflowRunContext context = runContext();
        WorkflowRunEntity run = runningRun();
        when(runRepo.lockByRunId("workflow-run-1")).thenReturn(Optional.of(run));
        when(eventRepo.nextSequence("workflow-run-1")).thenReturn(3L);
        Instant startedAt = Instant.parse("2026-08-09T06:00:00Z");
        WorkflowNodeTrace trace = WorkflowNodeTrace.builder()
                .nodeId("start")
                .nodeType(WorkflowNodeType.START)
                .status(WorkflowNodeStatus.FAILED)
                .startedAt(startedAt)
                .finishedAt(startedAt.plusMillis(25))
                .inputs(Map.of("query", "不应持久化"))
                .outputs(Map.of("answer", "不应持久化"))
                .errorMessage("内部异常")
                .build();

        service.finishNode(context, startNode(), trace);

        ArgumentCaptor<WorkflowNodeAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(WorkflowNodeAttemptEntity.class);
        verify(attemptRepo).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue()).satisfies(attempt -> {
            assertThat(attempt.getStatus()).isEqualTo(WorkflowNodeRunStatus.FAILED);
            assertThat(attempt.getDurationMillis()).isEqualTo(25L);
            assertThat(attempt.getErrorCode()).isEqualTo("WORKFLOW_NODE_FAILED");
        });
        ArgumentCaptor<WorkflowRunEventEntity> eventCaptor = ArgumentCaptor.forClass(WorkflowRunEventEntity.class);
        verify(eventRepo).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(WorkflowRunEventType.NODE_FAILED);
            assertThat(event.getMessage()).isEqualTo("节点执行失败");
        });
    }

    @Test
    void shouldPersistSafeIntentDecisionAndModelFacts() {
        WorkflowRunContext context = runContext();
        when(runRepo.lockByRunId("workflow-run-1")).thenReturn(Optional.of(runningRun()));
        when(eventRepo.nextSequence("workflow-run-1")).thenReturn(4L);
        Instant startedAt = Instant.parse("2026-08-09T06:00:00Z");
        WorkflowNodeTrace trace = WorkflowNodeTrace.builder()
                .nodeId("intent")
                .nodeType(WorkflowNodeType.INTENT_CLASSIFIER)
                .status(WorkflowNodeStatus.SUCCEEDED)
                .startedAt(startedAt)
                .finishedAt(startedAt.plusMillis(30))
                .outputs(Map.of("intentCode", "refund", "recognizedSlots", List.of("不应持久化")))
                .decisionStatus(IntentClassifierNodeStatus.SELECTED)
                .reasonCode("SINGLE_INTENT")
                .modelInstanceId(31L)
                .promptVersionId(32L)
                .tokenUsage(new TokenUsage(8, 3, 11))
                .modelCalls(1)
                .retryCount(0)
                .build();
        WorkflowNodeDefinition node = new WorkflowNodeDefinition(
                "intent", "识别意图", WorkflowNodeType.INTENT_CLASSIFIER,
                new com.lingxi.workbench.workflow.model.config.IntentClassifierNodeConfig(
                        30L, 40L, "query", Map.of("status", "intentStatus", "reasonCode", "intentReason")));

        service.finishNode(context, node, trace);

        ArgumentCaptor<WorkflowNodeAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(WorkflowNodeAttemptEntity.class);
        verify(attemptRepo).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue()).satisfies(attempt -> {
            assertThat(attempt.getDecisionStatus()).isEqualTo(IntentClassifierNodeStatus.SELECTED);
            assertThat(attempt.getReasonCode()).isEqualTo("SINGLE_INTENT");
            assertThat(attempt.getModelInstanceId()).isEqualTo(31L);
            assertThat(attempt.getPromptVersionId()).isEqualTo(32L);
            assertThat(attempt.getModelCalls()).isEqualTo(1);
            assertThat(attempt.getTotalTokens()).isEqualTo(11);
        });
        ArgumentCaptor<WorkflowRunEventEntity> eventCaptor = ArgumentCaptor.forClass(WorkflowRunEventEntity.class);
        verify(eventRepo).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.getDecisionStatus()).isEqualTo(IntentClassifierNodeStatus.SELECTED);
            assertThat(event.getReasonCode()).isEqualTo("SINGLE_INTENT");
            assertThat(event.getTotalTokens()).isEqualTo(11);
        });
    }

    @Test
    void shouldRejectRepeatedRunCompletion() {
        WorkflowRunEntity run = runningRun();
        when(runRepo.lockByRunId("workflow-run-1")).thenReturn(Optional.of(run));
        when(runRepo.finish(org.mockito.ArgumentMatchers.eq("workflow-run-1"), org.mockito.ArgumentMatchers.eq(WorkflowRunStatus.SUCCEEDED), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.succeed(runContext()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复结束");
    }

    private static WorkflowRunContext runContext() {
        WorkflowDefinition definition = new WorkflowDefinition(2, "test", List.of(startNode()), List.of());
        return WorkflowRunContext.builder()
                .runId("workflow-run-1")
                .definition(definition)
                .request(new WorkflowExecutionRequest("execution-1", 10L, 11L, Map.of("query", "测试")))
                .ports(new WorkflowExecutionPorts(invocation -> Map.of(), invocation -> mock(IntentClassifierNodeResult.class), null))
                .startedAt(Instant.parse("2026-08-09T06:00:00Z"))
                .build();
    }

    private static WorkflowNodeDefinition startNode() {
        return new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig());
    }

    private static WorkflowRunEntity runningRun() {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setRunId("workflow-run-1");
        run.setStatus(WorkflowRunStatus.RUNNING);
        return run;
    }
}
