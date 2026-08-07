package com.lingxi.workbench.admin.agent.service.impl;

import com.lingxi.workbench.admin.agent.repo.AgentRepo;
import com.lingxi.workbench.admin.agent.repo.AgentVersionRepo;
import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 基于 Agent 控制面仓储提供稳定的跨配置引用查询。 */
@Service
@RequiredArgsConstructor
public class AgentReferenceServiceImpl implements AgentReferenceService {

    private final AgentRepo agentRepo;
    private final AgentVersionRepo agentVersionRepo;

    @Override
    public Optional<AgentReference> findAgent(Long agentId) {
        return agentRepo.findById(agentId).map(agent -> new AgentReference(
                new AgentIdentity(agent.getId(), agent.getName(), agent.getDescription(), agent.getAgentType()),
                agent.getStatus() == EnableStatus.ACTIVE,
                agent.getPublishedVersionId()));
    }

    @Override
    public Optional<Long> findVersion(Long agentId, Long versionId) {
        return agentVersionRepo.findByAgentIdAndId(agentId, versionId)
                .map(version -> version.getId());
    }
}
