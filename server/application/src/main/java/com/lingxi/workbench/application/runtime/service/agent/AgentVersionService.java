package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeAgentVersionEntity;
import com.lingxi.workbench.application.runtime.repo.AgentRuntimeRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentVersionService {

    private final AgentRuntimeRepo repo;

    /** 从数据库读取能力锁定的 AgentVersion，并构建经过协议校验的不可变运行快照。 */
    @Transactional(readOnly = true)
    public AgentRuntimeSnapshot resolveVersion(Long agentId, Long agentVersionId) {
        RuntimeAgentVersionEntity version = repo.findVersion(agentId, agentVersionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "引用的AgentVersion不存在"));
        if (Objects.isNull(version.getDefinitionSnapshot())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少运行快照");
        }
        try {
            return new AgentRuntimeSnapshot(
                    version.getAgentId(), version.getId(), version.getDefinitionSnapshot());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion运行协议不受支持");
        }
    }
}
