package com.lingxi.workbench.application.runtime.model;

/**
 * Workflow 节点锁定的 AgentVersion 运行快照。
 *
 * @param agentId        Agent 主键
 * @param agentVersionId AgentVersion 主键
 * @param definition     AgentVersion 的不可变运行定义
 */
public record AgentRuntimeSnapshot(
        Long agentId,
        Long agentVersionId,
        AgentRuntimeDefinition definition) {
}
