package com.lingxi.workbench.admin.agent.service.impl;

import com.lingxi.workbench.admin.agent.repo.AgentRepo;
import com.lingxi.workbench.admin.agent.repo.AgentVersionRepo;
import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;
import java.util.Set;

/** 基于 Agent 控制面仓储提供稳定的跨配置引用查询。 */
@Service
@RequiredArgsConstructor
public class AgentReferenceServiceImpl implements AgentReferenceService {

    private final AgentRepo agentRepo;
    private final AgentVersionRepo agentVersionRepo;

    @Override
    public Optional<AgentReference> findAgent(Long agentId) {
        return agentRepo.findById(agentId).map(agent -> new AgentReference(
                new AgentIdentity(agent.getId(), agent.getName(), agent.getDescription(), agent.getRole()),
                agent.getStatus() == EnableStatus.ACTIVE,
                agent.getPublishedVersionId(),
                agent.getDomainId()));
    }

    @Override
    public Optional<AgentVersionReference> findVersion(Long agentId, Long versionId) {
        return agentVersionRepo.findByAgentIdAndId(agentId, versionId)
                .filter(version -> version.getSchemaVersion() != null
                        && version.getDefinitionSnapshot() != null
                        && version.getDefinitionSnapshot().getRole() != null)
                .map(version -> new AgentVersionReference(
                        version.getId(),
                        version.getSchemaVersion(),
                        version.getDefinitionSnapshot().getRole()));
    }

    @Override
    public List<AgentReference> listPublishedSpecialists(Set<Long> domainIds) {
        return agentRepo.findPublishedSpecialistsByDomainIds(domainIds).stream()
                .map(agent -> new AgentReference(
                        new AgentIdentity(agent.getId(), agent.getName(), agent.getDescription(), agent.getRole()),
                        true,
                        agent.getPublishedVersionId(),
                        agent.getDomainId()))
                .toList();
    }
}
