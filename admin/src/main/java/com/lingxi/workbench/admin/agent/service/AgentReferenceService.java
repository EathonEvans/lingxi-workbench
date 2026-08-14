package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;

import java.util.Optional;
import java.util.List;
import java.util.Set;

/** 为 Application 和 Workflow 发布校验提供只读 Agent 引用。 */
public interface AgentReferenceService {

    Optional<AgentReference> findAgent(Long agentId);

    Optional<AgentVersionReference> findVersion(Long agentId, Long versionId);

    /** 查询领域范围内具有正式版本的启用专家 Agent。 */
    List<AgentReference> listPublishedSpecialists(Set<Long> domainIds);

    /**
     * @param id Agent 主键
     * @param name Agent 名称
     * @param description Agent 职责描述
     * @param role Agent 类型
     */
    record AgentIdentity(Long id, String name, String description, AgentRole role) {
    }

    /**
     * @param identity Agent 稳定身份
     * @param active 是否启用
     * @param publishedVersionId 当前生效版本主键
     * @param domainId Agent 所属领域主键
     */
    record AgentReference(AgentIdentity identity, boolean active, Long publishedVersionId, Long domainId) {
    }

    /**
     * 发布快照的最小跨模块协议元数据。
     *
     * @param id           Agent 版本主键
     * @param schemaVersion 发布时冻结的版本 Schema 号
     * @param role     Agent 类型
     */
    record AgentVersionReference(Long id, int schemaVersion, AgentRole role) {
    }
}
