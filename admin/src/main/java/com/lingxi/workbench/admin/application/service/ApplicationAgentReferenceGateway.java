package com.lingxi.workbench.admin.application.service;

import java.util.Optional;

/** Workflow 发布校验读取 Agent 稳定身份和不可变版本的端口。 */
public interface ApplicationAgentReferenceGateway {

    /** 按主键查询 Agent 引用信息（激活状态、生效版本）。 */
    Optional<AgentReference> findAgent(Long agentId);

    /** 校验版本是否存在且属于指定 Agent，返回版本 ID。 */
    Optional<Long> findVersion(Long agentId, Long versionId);

    record AgentReference(Long id, boolean active, Long publishedVersionId) {
    }
}
