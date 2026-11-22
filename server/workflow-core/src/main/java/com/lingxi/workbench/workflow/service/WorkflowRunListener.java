package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;

import java.time.Instant;

/** 接收 Workflow 运行和节点状态变化的引擎边界。 */
public interface WorkflowRunListener {

    default void onRunStarted(WorkflowRunContext context) {
    }

    default void onNodeStarted(WorkflowRunContext context, WorkflowNodeDefinition node, Instant startedAt) {
    }

    default void onNodeSucceeded(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace) {
    }

    default void onNodeFailed(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace) {
    }

    default void onRunSucceeded(WorkflowRunContext context, WorkflowExecutionResult result) {
    }

    default void onRunFailed(WorkflowRunContext context, Throwable error) {
    }

    static WorkflowRunListener noop() {
        return new WorkflowRunListener() {
        };
    }
}
