package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;

import java.util.Optional;

/** Workflow 发布校验读取 Agent 稳定身份和不可变版本的端口。 */
public interface ApplicationAgentReferenceGateway {

    /** 按主键查询 Agent 引用信息（激活状态、生效版本）。 */
    Optional<AgentReference> findAgent(Long agentId);

    /** 校验版本是否存在且属于指定 Agent，返回版本 ID。 */
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
