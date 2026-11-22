package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeAgentVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeAgentVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** AgentVersion 发布快照的运行端只读仓储。 */
@Repository
@RequiredArgsConstructor
public class AgentRuntimeRepo {

    private final RuntimeAgentVersionMapper mapper;

    /** 按主键和 Agent 归属查询 AgentVersion 实体，过滤非当前 Agent 的版本。 */
    public Optional<RuntimeAgentVersionEntity> findVersion(Long agentId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<RuntimeAgentVersionEntity>lambdaQuery()
                        .eq(RuntimeAgentVersionEntity::getId, versionId)
                        .eq(RuntimeAgentVersionEntity::getAgentId, agentId)));
    }
}
