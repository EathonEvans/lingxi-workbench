package com.lingxi.workbench.admin.application.service.impl;

import com.lingxi.workbench.admin.agent.repo.AgentRepo;
import com.lingxi.workbench.admin.agent.repo.AgentVersionRepo;
import com.lingxi.workbench.admin.application.service.ApplicationAgentReferenceGateway;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 使用 Agent 控制面仓储实现 Application 的只读引用查询端口。 */
@Component
@RequiredArgsConstructor
public class RepositoryApplicationAgentReferenceGateway
        implements ApplicationAgentReferenceGateway {
    private final AgentRepo agentRepo;
    private final AgentVersionRepo agentVersionRepo;

    @Override
    /** 通过 Agent 仓储查询 Agent 引用状态。 */
    public Optional<AgentReference> findAgent(Long agentId) {
        return agentRepo.findById(agentId).map(agent -> new AgentReference(
                new AgentIdentity(agent.getId(), agent.getName(), agent.getDescription(), agent.getAgentType()),
                agent.getStatus() == EnableStatus.ACTIVE,
                agent.getPublishedVersionId()));
    }

    @Override
    /** 通过版本仓储校验 Agent 版本存在性。 */
    public Optional<Long> findVersion(Long agentId, Long versionId) {
        return agentVersionRepo.findByAgentIdAndId(agentId, versionId)
                .map(version -> version.getId());
    }
}
