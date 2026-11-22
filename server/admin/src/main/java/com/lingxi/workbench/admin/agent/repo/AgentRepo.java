package com.lingxi.workbench.admin.agent.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.request.AgentPageQuery;
import com.lingxi.workbench.admin.agent.repo.mapper.AgentMapper;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Set;

/** Agent 主记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AgentRepo {
    private final AgentMapper mapper;

    /** 按关键字、类型和状态分页查询 Agent 列表。 */
    public PageResult<AgentEntity> findPage(AgentPageQuery query) {
        IPage<AgentEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<AgentEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(AgentEntity::getName, query.getKeyword())
                                .or()
                                .like(AgentEntity::getCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getRole()),
                                AgentEntity::getRole, query.getRole())
                        .eq(Objects.nonNull(query.getDomainId()),
                                AgentEntity::getDomainId, query.getDomainId())
                        .eq(Objects.nonNull(query.getStatus()),
                                AgentEntity::getStatus, query.getStatus())
                        .orderByDesc(AgentEntity::getUpdatedAt)
                        .orderByDesc(AgentEntity::getId));
        return PageResult.convert(page);
    }

    /** 按主键查询 Agent。 */
    public Optional<AgentEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 带行锁按主键查询 Agent，用于发布等写操作。 */
    public Optional<AgentEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentEntity>lambdaQuery()
                        .eq(AgentEntity::getId, id)
                        .last("FOR UPDATE")));
    }

    /** 查询指定领域节点内具有正式版本的启用领域专家。 */
    public List<AgentEntity> findPublishedSpecialistsByDomainIds(Set<Long> domainIds) {
        if (domainIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getRole, AgentRole.SPECIALIST)
                .eq(AgentEntity::getStatus, EnableStatus.ACTIVE)
                .isNotNull(AgentEntity::getPublishedVersionId)
                .in(AgentEntity::getDomainId, domainIds)
                .orderByAsc(AgentEntity::getName, AgentEntity::getId)
                .last("LIMIT 1000"));
    }

    /** 校验 Agent 编码唯一性，支持排除指定记录。 */
    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), AgentEntity::getId, excludedId));
    }

    public void save(AgentEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
