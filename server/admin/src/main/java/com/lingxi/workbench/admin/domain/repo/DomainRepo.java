package com.lingxi.workbench.admin.domain.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.repo.mapper.DomainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 领域持久化仓库。 */
@Repository
@RequiredArgsConstructor
public class DomainRepo {
    private final DomainMapper mapper;

    public List<DomainEntity> findAll() {
        return mapper.selectList(Wrappers.<DomainEntity>lambdaQuery()
                .orderByAsc(DomainEntity::getSortOrder, DomainEntity::getId)
                .last("LIMIT 1000"));
    }

    public Optional<DomainEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 查询一级领域下全部启用节点，发布时用于解析可用 Agent 范围。 */
    public List<DomainEntity> findActiveByRootId(Long rootId) {
        return mapper.selectList(Wrappers.<DomainEntity>lambdaQuery()
                .eq(DomainEntity::getRootId, rootId)
                .eq(DomainEntity::getStatus, EnableStatus.ACTIVE)
                .orderByAsc(DomainEntity::getDepth, DomainEntity::getSortOrder, DomainEntity::getId)
                .last("LIMIT 1000"));
    }

    public boolean existsByCode(String code) {
        return mapper.exists(Wrappers.<DomainEntity>lambdaQuery().eq(DomainEntity::getCode, code));
    }

    public boolean hasChildren(Long id) {
        return mapper.exists(Wrappers.<DomainEntity>lambdaQuery().eq(DomainEntity::getParentId, id));
    }

    public long countRefs(Long id) {
        return mapper.countRefs(id);
    }

    public void insert(DomainEntity entity) {
        mapper.insert(entity);
    }

    public void update(DomainEntity entity) {
        mapper.updateById(entity);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
