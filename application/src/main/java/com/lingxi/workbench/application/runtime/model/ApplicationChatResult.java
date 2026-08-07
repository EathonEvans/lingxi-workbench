package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;

import java.util.List;

/**
 * Application 意图目标执行完成后的业务结果。
 *
 * @param answer          模型最终回答文本
 * @param tokenUsage      执行过程中的累计 Token 用量
 * @param traces          Workflow 各节点执行轨迹；非 Workflow 目标为空
 * @param modelInstanceId 实际执行目标的入口模型实例；固定答复和澄清为空
 */
public record ApplicationChatResult(
        String answer,
        TokenUsage tokenUsage,
        List<WorkflowNodeTrace> traces,
        Long modelInstanceId) {

    public ApplicationChatResult(String answer, TokenUsage tokenUsage, List<WorkflowNodeTrace> traces) {
        this(answer, tokenUsage, traces, null);
    }

    public ApplicationChatResult {
        traces = traces == null ? List.of() : List.copyOf(traces);
    }
}
