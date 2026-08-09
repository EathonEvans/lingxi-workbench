package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.service.WorkflowRunListener;
import com.lingxi.workbench.workflow.service.WorkflowRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 持久化 Workflow 运行并向当前 Chat 发布安全节点状态。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRunListenerImpl implements WorkflowRunListener {

    private final WorkflowRunService runService;
    private final ChatExecutionContextRegistry executionContextRegistry;

    @Override
    public void onRunStarted(WorkflowRunContext context) {
        runService.start(context, executionContext(context));
    }

    @Override
    public void onNodeStarted(WorkflowRunContext context, WorkflowNodeDefinition node, Instant startedAt) {
        runService.startNode(context, node, startedAt);
        publishNodeEvent(context, node, ExecutionEventType.STEP_STARTED, ExecutionStepStatus.RUNNING, "节点开始执行");
    }

    @Override
    public void onNodeSucceeded(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace) {
        runService.finishNode(context, node, trace);
        publishNodeEvent(context, node, ExecutionEventType.STEP_COMPLETED, ExecutionStepStatus.COMPLETED, "节点执行成功");
    }

    @Override
    public void onNodeFailed(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace) {
        runService.finishNode(context, node, trace);
        publishNodeEvent(context, node, ExecutionEventType.STEP_FAILED, ExecutionStepStatus.FAILED, "节点执行失败");
    }

    @Override
    public void onRunSucceeded(WorkflowRunContext context, WorkflowExecutionResult result) {
        runService.succeed(context);
    }

    @Override
    public void onRunFailed(WorkflowRunContext context, Throwable error) {
        runService.fail(context);
    }

    private ChatExecutionContext executionContext(WorkflowRunContext context) {
        return executionContextRegistry.require(context.getRequest().executionId());
    }

    private void publishNodeEvent(WorkflowRunContext context, WorkflowNodeDefinition node, ExecutionEventType eventType, ExecutionStepStatus status, String message) {
        try {
            executionContext(context).getEventPublisher().publishExecution(ChatExecutionTimelineEventBO.builder()
                    .runId(context.getRunId())
                    .eventType(eventType)
                    .stepId(node.id())
                    .displayName(node.name())
                    .stepStatus(status)
                    .message(message)
                    .build());
        } catch (RuntimeException exception) {
            // SSE 仅用于前端观察，发布失败不能改变已经持久化的节点执行事实。
            log.warn("Workflow节点事件发布失败, runId={}, nodeId={}, eventType={}", context.getRunId(), node.id(), eventType, exception);
        }
    }
}
