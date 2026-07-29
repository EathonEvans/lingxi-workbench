package com.lingxi.workbench.workflow.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Agent 节点调用参数，封装工作流引擎向 Agent 运行时发起的单次调用上下文。
 *
 * <p>该对象包含执行身份、节点身份、AgentVersion 和输入变量六个字段，
 * 使用 Builder 可以避免多个字符串与 Long 类型的位置参数错位。</p>
 */
@Getter
public class AgentNodeInvocation {

    /**
     * 本次 Workflow 执行的唯一标识。
     */
    private final String executionId;
    /**
     * Application 发布版本 ID，用于版本锁定和审计。
     */
    private final String applicationVersionId;
    /**
     * Workflow 中发起调用的 Agent 节点 ID。
     */
    private final String nodeId;
    /**
     * 目标 Agent 主键。
     */
    private final Long agentId;
    /**
     * 目标 AgentVersion 主键。
     */
    private final Long agentVersionId;
    /**
     * 从 Workflow 变量中按 inputMapping 解析出的 Agent 输入。
     */
    private final Map<String, Object> inputs;

    @Builder
    private AgentNodeInvocation(
            String executionId,
            String applicationVersionId,
            String nodeId,
            Long agentId,
            Long agentVersionId,
            Map<String, Object> inputs) {
        this.executionId = executionId;
        this.applicationVersionId = applicationVersionId;
        this.nodeId = nodeId;
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}
