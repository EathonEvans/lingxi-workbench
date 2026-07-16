package com.lingxi.workbench.admin.model.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.repo.mapper.ModelInstanceMapper;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 模型实例数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelInstanceRepo {

    private final ModelInstanceMapper mapper;

    /** 分页查询模型实例。 */
    public PageResult<ModelInstanceEntity> findPage(PageQuery query) {
        IPage<ModelInstanceEntity> page = mapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ModelInstanceEntity>lambdaQuery()
                        .orderByAsc(ModelInstanceEntity::getId));
        return toPageResult(page);
    }

    public Optional<ModelInstanceEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCode, code)
                .ne(excludedId != null, ModelInstanceEntity::getId, excludedId));
    }

    public boolean existsActiveByProviderId(Long providerId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getProviderId, providerId)
                .eq(ModelInstanceEntity::getStatus, "ACTIVE"));
    }

    public boolean existsByCredentialId(Long credentialId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCredentialId, credentialId));
    }

    public boolean existsActiveByCredentialId(Long credentialId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCredentialId, credentialId)
                .eq(ModelInstanceEntity::getStatus, "ACTIVE"));
    }

    public void save(ModelInstanceEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    public void clearDefault(String type) {
        mapper.clearDefault(type);
    }

    private static <T> PageResult<T> toPageResult(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }
}
