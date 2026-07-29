package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;

import java.util.List;

/**
 * Workflow 对话执行完成后的业务结果。
 *
 * @param answer     模型最终回答文本
 * @param tokenUsage Workflow 执行过程中的累计 Token 用量
 * @param traces     Workflow 各节点执行轨迹
 */
public record WorkflowChatResult(
        String answer,
        MessageTokenUsageBO tokenUsage,
        List<WorkflowNodeTrace> traces) {

    public WorkflowChatResult {
        traces = traces == null ? List.of() : List.copyOf(traces);
    }
}
