package com.lingxi.workbench.admin.model.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.admin.model.model.entity.*;
import com.lingxi.workbench.admin.model.repo.mapper.*;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/** 模型配置数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelConfigRepo {
    private final ModelProviderMapper providerMapper;
    private final ModelCredentialMapper credentialMapper;
    private final ModelInstanceMapper instanceMapper;
    private final ModelAuditMapper auditMapper;
    public List<ModelProviderEntity> providers() {
        return providerMapper.selectList(
                Wrappers.<ModelProviderEntity>lambdaQuery().orderByAsc(ModelProviderEntity::getId));
    }

    /** 分页查询模型供应商。 */
    public PageResult<ModelProviderEntity> providerPage(PageQuery query) {
        IPage<ModelProviderEntity> page = providerMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ModelProviderEntity>lambdaQuery().orderByAsc(ModelProviderEntity::getId));
        return toPageResult(page);
    }

    public Optional<ModelProviderEntity> provider(Long id) {
        return Optional.ofNullable(providerMapper.selectById(id));
    }

    public boolean providerCodeExists(String code, Long excludedId) {
        return providerMapper.exists(Wrappers.<ModelProviderEntity>lambdaQuery()
                .eq(ModelProviderEntity::getCode, code)
                .ne(excludedId != null, ModelProviderEntity::getId, excludedId));
    }

    public void saveProvider(ModelProviderEntity entity) {
        if (entity.getId() == null) {
            providerMapper.insert(entity);
        } else {
            providerMapper.updateById(entity);
        }
    }

    public List<ModelCredentialEntity> credentials() {
        return credentialMapper.selectList(
                Wrappers.<ModelCredentialEntity>lambdaQuery().orderByAsc(ModelCredentialEntity::getId));
    }

    /** 分页查询模型访问密钥。 */
    public PageResult<ModelCredentialEntity> credentialPage(PageQuery query) {
        IPage<ModelCredentialEntity> page = credentialMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ModelCredentialEntity>lambdaQuery().orderByAsc(ModelCredentialEntity::getId));
        return toPageResult(page);
    }

    public Optional<ModelCredentialEntity> credential(Long id) {
        return Optional.ofNullable(credentialMapper.selectById(id));
    }

    public boolean credentialNameExists(Long providerId, String name) {
        return credentialMapper.exists(Wrappers.<ModelCredentialEntity>lambdaQuery()
                .eq(ModelCredentialEntity::getProviderId, providerId)
                .eq(ModelCredentialEntity::getName, name));
    }

    public void saveCredential(ModelCredentialEntity entity) {
        if (entity.getId() == null) {
            credentialMapper.insert(entity);
        } else {
            credentialMapper.updateById(entity);
        }
    }

    public List<ModelInstanceEntity> instances() {
        return instanceMapper.selectList(
                Wrappers.<ModelInstanceEntity>lambdaQuery().orderByAsc(ModelInstanceEntity::getId));
    }

    /** 分页查询模型实例。 */
    public PageResult<ModelInstanceEntity> instancePage(PageQuery query) {
        IPage<ModelInstanceEntity> page = instanceMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ModelInstanceEntity>lambdaQuery().orderByAsc(ModelInstanceEntity::getId));
        return toPageResult(page);
    }

    public Optional<ModelInstanceEntity> instance(Long id) {
        return Optional.ofNullable(instanceMapper.selectById(id));
    }

    public boolean instanceCodeExists(String code, Long excludedId) {
        return instanceMapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCode, code)
                .ne(excludedId != null, ModelInstanceEntity::getId, excludedId));
    }

    public boolean providerHasActiveInstance(Long providerId) {
        return instanceMapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getProviderId, providerId)
                .eq(ModelInstanceEntity::getStatus, "ACTIVE"));
    }

    public boolean credentialReferenced(Long credentialId) {
        return instanceMapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCredentialId, credentialId));
    }

    public boolean credentialUsedByActiveInstance(Long credentialId) {
        return instanceMapper.exists(Wrappers.<ModelInstanceEntity>lambdaQuery()
                .eq(ModelInstanceEntity::getCredentialId, credentialId)
                .eq(ModelInstanceEntity::getStatus, "ACTIVE"));
    }

    public void saveInstance(ModelInstanceEntity entity) {
        if (entity.getId() == null) {
            instanceMapper.insert(entity);
        } else {
            instanceMapper.updateById(entity);
        }
    }

    public void clearDefault(String type) {
        instanceMapper.clearDefault(type);
    }

    public void audit(String type, Long id, Long operator, String action, String summary) {
        auditMapper.insert(type, id, operator, action, summary);
    }

    /** 将持久层分页结果转换为项目统一分页响应。 */
    private static <T> PageResult<T> toPageResult(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }
}
