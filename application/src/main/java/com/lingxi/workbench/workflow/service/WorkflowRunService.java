package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.model.entity.WorkflowNodeAttemptEntity;
import com.lingxi.workbench.workflow.model.entity.WorkflowRunEventEntity;

import java.time.Instant;
import java.util.List;

/** Workflow 运行、节点尝试和事件的事务边界。 */
public interface WorkflowRunService {

    void start(WorkflowRunContext context, ChatExecutionContext executionContext);

    void startNode(WorkflowRunContext context, WorkflowNodeDefinition node, Instant startedAt);

    void finishNode(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace);

    void succeed(WorkflowRunContext context);

    void fail(WorkflowRunContext context);

    List<WorkflowNodeAttemptEntity> findNodeAttempts(String runId);

    List<WorkflowRunEventEntity> findEventsAfter(String runId, long afterSequence);
}
