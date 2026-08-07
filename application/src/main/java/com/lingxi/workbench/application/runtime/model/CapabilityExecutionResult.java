package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;

import java.util.List;

/**
 * @param content         能力的结构化文本结果
 * @param modelInstanceId Agent或Workflow入口模型实例
 * @param traces          Workflow节点轨迹
 */
public record CapabilityExecutionResult(String content, Long modelInstanceId, List<WorkflowNodeTrace> traces) {

    public CapabilityExecutionResult {
        traces = traces == null ? List.of() : List.copyOf(traces);
    }
}
