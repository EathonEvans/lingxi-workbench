package com.lingxi.workbench.admin.model.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.request.ModelInstancePageQuery;
import com.lingxi.workbench.admin.model.repo.mapper.ModelInstanceMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** 模型实例数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelInstanceRepo {

    private final ModelInstanceMapper mapper;

    /** 分页查询模型实例。 */
    public PageResult<ModelInstanceEntity> findPage(ModelInstancePageQuery query) {
        IPage<ModelInstanceEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<ModelInstanceEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(ModelInstanceEntity::getName, query.getKeyword())
                                .or()
                                .like(ModelInstanceEntity::getCode, query.getKeyword())
                                .or()
                                .like(ModelInstanceEntity::getModelCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getProviderId()),
                                ModelInstanceEntity::getProviderId,
                                query.getProviderId())
                        .eq(Objects.nonNull(query.getModelType()),
                                ModelInstanceEntity::getModelType,
                                query.getModelType())
                        .eq(Objects.nonNull(query.getStatus()),
                                ModelInstanceEntity::getStatus,
                                query.getStatus())
                        .orderByAsc(ModelInstanceEntity::getId));
        return PageResult.convert(page);
    }

    public Optional<ModelInstanceEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), ModelInstanceEntity::getId, excludedId));
    }

    public boolean existsActiveByProviderId(Long providerId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getProviderId, providerId)
                .eq(ModelInstanceEntity::getStatus, EnableStatus.ACTIVE));
    }

    public boolean existsByCredentialId(Long credentialId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCredentialId, credentialId));
    }

    public boolean existsActiveByCredentialId(Long credentialId) {
        return mapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCredentialId, credentialId)
                .eq(ModelInstanceEntity::getStatus, EnableStatus.ACTIVE));
    }

    public void save(ModelInstanceEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    public void clearDefault(ModelType type) {
        mapper.clearDefault(type);
    }

}
