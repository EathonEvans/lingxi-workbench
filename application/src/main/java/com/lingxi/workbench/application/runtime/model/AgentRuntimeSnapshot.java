package com.lingxi.workbench.application.runtime.model;

import java.util.Objects;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;

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

    public AgentRuntimeSnapshot {
        Objects.requireNonNull(agentId, "agentId不能为空");
        Objects.requireNonNull(agentVersionId, "agentVersionId不能为空");
        Objects.requireNonNull(definition, "AgentVersion运行定义不能为空");
        if (!Objects.equals(definition.getSchemaVersion(), AGENT_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("AgentVersion运行定义版本不受支持");
        }
        Objects.requireNonNull(definition.getRole(), "AgentVersion类型不能为空");
    }
}
