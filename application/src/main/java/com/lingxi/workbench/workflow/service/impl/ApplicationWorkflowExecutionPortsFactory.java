package com.lingxi.workbench.workflow.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.WorkflowRunStartCommand;
import com.lingxi.workbench.application.runtime.service.agent.ChatAgentNodeInvoker;
import com.lingxi.workbench.application.runtime.service.routing.WorkflowIntentClassifierNodeInvoker;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowExecutionPorts;
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

/** 为每次 Workflow 执行创建绑定可信 Chat 上下文的节点调用与运行监听端口。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationWorkflowExecutionPortsFactory {

    private final WorkflowRunService runService;
    private final ChatAgentNodeInvoker agentNodeInvoker;
    private final WorkflowIntentClassifierNodeInvoker intentClassifierNodeInvoker;

    public WorkflowExecutionPorts create(ChatExecutionContext executionContext) {
        WorkflowRunListener runListener = new BoundWorkflowRunListener(runService, executionContext,
                startCommand(executionContext));
        return new WorkflowExecutionPorts(
                invocation -> agentNodeInvoker.invoke(invocation, executionContext),
                invocation -> intentClassifierNodeInvoker.invoke(invocation, executionContext),
                runListener);
    }

    private record BoundWorkflowRunListener(WorkflowRunService runService,
                                            ChatExecutionContext executionContext,
                                            WorkflowRunStartCommand startCommand) implements WorkflowRunListener {

        private BoundWorkflowRunListener {
            java.util.Objects.requireNonNull(runService, "Workflow运行服务不能为空");
            java.util.Objects.requireNonNull(executionContext, "Chat执行上下文不能为空");
            java.util.Objects.requireNonNull(startCommand, "Workflow运行启动命令不能为空");
        }

        @Override
        public void onRunStarted(WorkflowRunContext context) {
            runService.start(context, startCommand);
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

        private void publishNodeEvent(WorkflowRunContext context, WorkflowNodeDefinition node, ExecutionEventType eventType, ExecutionStepStatus status, String message) {
            try {
                executionContext.getEventPublisher().publishExecution(ChatExecutionTimelineEventBO.builder()
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

    private static WorkflowRunStartCommand startCommand(ChatExecutionContext executionContext) {
        ConversationContext conversation = java.util.Objects.requireNonNull(executionContext.getConversationContext(),
                "Workflow缺少共享对话上下文");
        return WorkflowRunStartCommand.builder()
                .assistantMessageId(executionContext.getTurn().assistantMessageId())
                .conversationId(executionContext.getTurn().conversationId())
                .userId(conversation.getLoginUser().id())
                .applicationId(conversation.getApplicationSnapshot().applicationId())
                .applicationVersionId(conversation.getApplicationSnapshot().versionId())
                .build();
    }
}
