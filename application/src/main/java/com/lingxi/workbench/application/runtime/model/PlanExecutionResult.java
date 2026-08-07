package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;

import java.util.List;

/**
 * @param content         最终回答或澄清问题
 * @param modelInstanceId 最终能力入口模型
 * @param traces          计划内全部Workflow轨迹
 */
public record PlanExecutionResult(String content, Long modelInstanceId, List<WorkflowNodeTrace> traces) {

    public PlanExecutionResult {
        traces = traces == null ? List.of() : List.copyOf(traces);
    }
}
