package com.lingxi.workbench.admin.agent.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionEntity;
import com.lingxi.workbench.admin.agent.repo.mapper.AgentVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Agent 发布版本数据仓库，只暴露插入和只读查询以保证版本不可变。 */
@Repository
@RequiredArgsConstructor
public class AgentVersionRepo {
    private final AgentVersionMapper mapper;

    /** 按主键查询版本。 */
    public Optional<AgentVersionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按 Agent ID 和版本 ID 联合查询，校验版本归属。 */
    public Optional<AgentVersionEntity> findByAgentIdAndId(Long agentId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentVersionEntity>lambdaQuery()
                        .eq(AgentVersionEntity::getAgentId, agentId)
                        .eq(AgentVersionEntity::getId, versionId)));
    }

    /** 查询某个 Agent 的所有历史版本。 */
    public List<AgentVersionEntity> findAll(Long agentId) {
        return mapper.selectList(Wrappers.<AgentVersionEntity>lambdaQuery()
                .eq(AgentVersionEntity::getAgentId, agentId)
                .orderByDesc(AgentVersionEntity::getVersionNo));
    }

    /** 批量按 Agent ID 列表查询关联的最新版本。 */
    public List<AgentVersionEntity> findAllByAgentIds(List<Long> agentIds) {
        if (CollUtil.isEmpty(agentIds)) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<AgentVersionEntity>lambdaQuery()
                .in(AgentVersionEntity::getAgentId, agentIds)
                .orderByDesc(AgentVersionEntity::getVersionNo));
    }

    /** 计算下一个版本号，从已有最大版本号 +1。 */
    public int nextVersionNo(Long agentId) {
        AgentVersionEntity latest = mapper.selectOne(
                Wrappers.<AgentVersionEntity>lambdaQuery()
                        .select(AgentVersionEntity::getVersionNo)
                        .eq(AgentVersionEntity::getAgentId, agentId)
                        .orderByDesc(AgentVersionEntity::getVersionNo)
                        .last("LIMIT 1"));
        return Objects.isNull(latest) ? 1 : latest.getVersionNo() + 1;
    }

    /** 按快照 SHA-256 校验是否已存在重复版本，避免重复发布。 */
    public boolean existsByChecksum(Long agentId, String checksum) {
        return mapper.exists(Wrappers.<AgentVersionEntity>lambdaQuery()
                .eq(AgentVersionEntity::getAgentId, agentId)
                .eq(AgentVersionEntity::getSnapshotChecksum, checksum));
    }

    public void insert(AgentVersionEntity entity) {
        mapper.insert(entity);
    }
}
