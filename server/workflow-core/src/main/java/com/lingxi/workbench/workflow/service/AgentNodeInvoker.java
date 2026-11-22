package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.workflow.model.AgentNodeInvocation;

import java.util.Map;

/**
 * Workflow 到现有 Agent 运行时的适配端口。
 *
 * <p>工作流执行到 Agent 节点时，通过该接口将节点输入转发给对应的 Agent 版本执行。
 * 正式接入时由适配器使用 agentVersionId 构造 ModelChatContext，调用现有 Chat/RAG 能力。</p>
 */
@FunctionalInterface
public interface AgentNodeInvoker {

    /**
     * 调用指定 Agent 版本执行一次推理，返回 Agent 输出的结构化结果。
     *
     * @param invocation Agent 节点调用上下文，包含执行 ID、节点 ID、Agent ID/版本和输入变量
     * @return Agent 推理输出，key 为 Agent 输出变量名，value 为对应结果
     */
    Map<String, Object> invoke(AgentNodeInvocation invocation);
}
