package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import com.lingxi.workbench.workflow.service.IntentClassifierNodeInvoker;
import com.lingxi.workbench.workflow.service.WorkflowRunListener;

import java.util.Objects;

/**
 * @param agentNodeInvoker 每次执行显式绑定的 Agent 节点调用边界
 * @param intentClassifierNodeInvoker 每次执行显式绑定的意图识别节点调用边界
 * @param runListener 每次执行显式绑定的运行事实与事件监听器
 */
public record WorkflowExecutionPorts(AgentNodeInvoker agentNodeInvoker,
                                     IntentClassifierNodeInvoker intentClassifierNodeInvoker,
                                     WorkflowRunListener runListener) {

    public WorkflowExecutionPorts {
        Objects.requireNonNull(agentNodeInvoker, "Agent节点调用边界不能为空");
        Objects.requireNonNull(intentClassifierNodeInvoker, "意图识别节点调用边界不能为空");
        runListener = Objects.requireNonNullElseGet(runListener, WorkflowRunListener::noop);
    }
}
