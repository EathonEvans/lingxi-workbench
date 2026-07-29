package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeAgentVersionEntity;
import com.lingxi.workbench.application.runtime.repo.AgentRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentRuntimeServiceImpl implements AgentRuntimeService {

    private final AgentRuntimeRepo repo;

    /**
     * 从数据库读取 Workflow 节点锁定的 AgentVersion 并构建不可变运行快照。
     * 校验 AgentVersion 存在且定义快照不为空，否则抛出业务异常。
     */
    @Override
    @Transactional(readOnly = true)
    public AgentRuntimeSnapshot resolveVersion(Long agentId, Long agentVersionId) {
        RuntimeAgentVersionEntity version = repo.findVersion(agentId, agentVersionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Workflow引用的AgentVersion不存在"));
        if (Objects.isNull(version.getDefinitionSnapshot())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少运行快照");
        }
        return new AgentRuntimeSnapshot(
                version.getAgentId(), version.getId(), version.getDefinitionSnapshot());
    }
}
