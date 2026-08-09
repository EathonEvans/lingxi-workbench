package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;

import java.util.Optional;

/** 为 Application 和 Workflow 发布校验提供只读 Agent 引用。 */
public interface AgentReferenceService {

    Optional<AgentReference> findAgent(Long agentId);

    Optional<Long> findVersion(Long agentId, Long versionId);

    /**
     * @param id Agent 主键
     * @param name Agent 名称
     * @param description Agent 职责描述
     * @param agentType Agent 类型
     */
    record AgentIdentity(Long id, String name, String description, AgentType agentType) {
    }

    /**
     * @param identity Agent 稳定身份
     * @param active 是否启用
     * @param publishedVersionId 当前生效版本主键
     */
    record AgentReference(AgentIdentity identity, boolean active, Long publishedVersionId) {
    }
}
