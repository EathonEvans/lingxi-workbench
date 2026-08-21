package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.common.enums.WorkflowNodeRunStatus;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunEventType;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.model.entity.WorkflowNodeAttemptEntity;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEntity;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEventEntity;
import com.lingxi.workbench.workflow.repo.WorkflowNodeAttemptRepo;
import com.lingxi.workbench.workflow.repo.WorkflowRunEventRepo;
import com.lingxi.workbench.workflow.repo.WorkflowRunRepo;
import com.lingxi.workbench.workflow.service.WorkflowRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Workflow 运行记录的事务实现。 */
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private static final String RUN_ERROR_CODE = "WORKFLOW_EXECUTION_FAILED";
    private static final String NODE_ERROR_CODE = "WORKFLOW_NODE_FAILED";

    private final WorkflowRunRepo runRepo;
    private final WorkflowNodeAttemptRepo attemptRepo;
    private final WorkflowRunEventRepo eventRepo;

    @Override
    @Transactional
    public void start(WorkflowRunContext context, ChatExecutionContext executionContext) {
        ConversationContext conversation = Objects.requireNonNull(executionContext.getConversationContext(), "Workflow缺少共享对话上下文");
        AppRuntimeSnapshot app = conversation.getApplicationSnapshot();
        ChatTurnBO turn = executionContext.getTurn();

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setRunId(context.getRunId());
        run.setExecutionId(context.getRequest().executionId());
        run.setAssistantMessageId(turn.assistantMessageId());
        run.setConversationId(turn.conversationId());
        run.setUserId(conversation.getLoginUser().id());
        run.setApplicationId(app.applicationId());
        run.setApplicationVersionId(app.versionId());
        run.setWorkflowId(context.getRequest().workflowId());
        run.setWorkflowVersionId(context.getRequest().workflowVersionId());
        run.setStatus(WorkflowRunStatus.RUNNING);
        run.setStartedAt(toOffsetDateTime(context.getStartedAt()));
        runRepo.insert(run);
        appendEvent(context.getRunId(), WorkflowRunEventType.RUN_STARTED, null, null, "Workflow开始执行", context.getStartedAt(), 1L);
    }

    @Override
    @Transactional
    public void startNode(WorkflowRunContext context, WorkflowNodeDefinition node, Instant startedAt) {
        requireRunning(context.getRunId());
        appendEvent(context.getRunId(), WorkflowRunEventType.NODE_STARTED, node, WorkflowNodeRunStatus.RUNNING, "节点开始执行", startedAt, eventRepo.nextSequence(context.getRunId()));
    }

    @Override
    @Transactional
    public void finishNode(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace) {
        requireRunning(context.getRunId());
        WorkflowNodeAttemptEntity attempt = new WorkflowNodeAttemptEntity();
        attempt.setRunId(context.getRunId());
        attempt.setNodeId(node.id());
        attempt.setNodeType(node.type());
        attempt.setAttemptNo(1);
        WorkflowNodeRunStatus nodeStatus = switch (trace.getStatus()) {
            case SUCCEEDED -> WorkflowNodeRunStatus.SUCCEEDED;
            case FAILED -> WorkflowNodeRunStatus.FAILED;
        };
        attempt.setStatus(nodeStatus);
        attempt.setStartedAt(toOffsetDateTime(trace.getStartedAt()));
        attempt.setFinishedAt(toOffsetDateTime(trace.getFinishedAt()));
        attempt.setDurationMillis(Duration.between(trace.getStartedAt(), trace.getFinishedAt()).toMillis());
        if (trace.getStatus() == WorkflowNodeStatus.FAILED) {
            attempt.setErrorCode(NODE_ERROR_CODE);
        }
        attemptRepo.insert(attempt);

        WorkflowRunEventType eventType = switch (trace.getStatus()) {
            case SUCCEEDED -> WorkflowRunEventType.NODE_SUCCEEDED;
            case FAILED -> WorkflowRunEventType.NODE_FAILED;
        };
        String message = switch (trace.getStatus()) {
            case SUCCEEDED -> "节点执行成功";
            case FAILED -> "节点执行失败";
        };
        appendEvent(context.getRunId(), eventType, node, nodeStatus, message, trace.getFinishedAt(), eventRepo.nextSequence(context.getRunId()));
    }

    @Override
    @Transactional
    public void succeed(WorkflowRunContext context) {
        finishRun(context, WorkflowRunStatus.SUCCEEDED, WorkflowRunEventType.RUN_SUCCEEDED, null, "Workflow执行成功");
    }

    @Override
    @Transactional
    public void fail(WorkflowRunContext context) {
        finishRun(context, WorkflowRunStatus.FAILED, WorkflowRunEventType.RUN_FAILED, RUN_ERROR_CODE, "Workflow执行失败");
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowNodeAttemptEntity> findNodeAttempts(String runId) {
        requireRunId(runId);
        return List.copyOf(attemptRepo.findByRunId(runId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRunEventEntity> findEventsAfter(String runId, long afterSequence) {
        requireRunId(runId);
        if (afterSequence < 0) {
            throw invalid("Workflow事件序号不能小于0");
        }
        return List.copyOf(eventRepo.findAfter(runId, afterSequence));
    }

    private void finishRun(WorkflowRunContext context, WorkflowRunStatus status, WorkflowRunEventType eventType, String errorCode, String message) {
        WorkflowRunEntity run = requireRunning(context.getRunId());
        OffsetDateTime finishedAt = OffsetDateTime.now();
        if (!runRepo.finish(run.getRunId(), status, finishedAt, errorCode)) {
            throw invalid("Workflow运行状态已变化，不能重复结束");
        }
        appendEvent(run.getRunId(), eventType, null, null, message, finishedAt.toInstant(), eventRepo.nextSequence(run.getRunId()));
    }

    private WorkflowRunEntity requireRunning(String runId) {
        requireRunId(runId);
        WorkflowRunEntity run = runRepo.lockByRunId(runId).orElseThrow(() -> invalid("Workflow运行不存在"));
        if (run.getStatus() != WorkflowRunStatus.RUNNING) {
            throw invalid("Workflow运行已结束");
        }
        return run;
    }

    private static void requireRunId(String runId) {
        if (Objects.isNull(runId) || runId.isBlank()) {
            throw invalid("Workflow runId不能为空");
        }
    }

    private void appendEvent(String runId, WorkflowRunEventType eventType, WorkflowNodeDefinition node, WorkflowNodeRunStatus status, String message, Instant occurredAt, long sequence) {
        WorkflowRunEventEntity event = new WorkflowRunEventEntity();
        event.setRunId(runId);
        event.setSequenceNo(sequence);
        event.setEventType(eventType);
        if (Objects.nonNull(node)) {
            event.setNodeId(node.id());
            event.setNodeType(node.type());
            event.setDisplayName(node.name());
        }
        event.setNodeStatus(status);
        event.setMessage(message);
        event.setOccurredAt(toOffsetDateTime(Objects.requireNonNullElseGet(occurredAt, Instant::now)));
        eventRepo.insert(event);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}
