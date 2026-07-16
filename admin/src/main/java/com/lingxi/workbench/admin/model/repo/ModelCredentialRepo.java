package com.lingxi.workbench.admin.model.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.repo.mapper.ModelCredentialMapper;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 模型访问密钥数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelCredentialRepo {

    private final ModelCredentialMapper mapper;

    /** 分页查询模型访问密钥。 */
    public PageResult<ModelCredentialEntity> findPage(PageQuery query) {
        IPage<ModelCredentialEntity> page = mapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ModelCredentialEntity>lambdaQuery()
                        .orderByAsc(ModelCredentialEntity::getId));
        return toPageResult(page);
    }

    /** 查询全部模型访问密钥，供模型实例选择。 */
    public List<ModelCredentialEntity> findAll() {
        return mapper.selectList(Wrappers.<ModelCredentialEntity>lambdaQuery()
                .orderByAsc(ModelCredentialEntity::getId));
    }

    public Optional<ModelCredentialEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByProviderIdAndName(Long providerId, String name) {
        return mapper.exists(Wrappers.<ModelCredentialEntity>lambdaQuery()
                .eq(ModelCredentialEntity::getProviderId, providerId)
                .eq(ModelCredentialEntity::getName, name));
    }

    public void save(ModelCredentialEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    private static <T> PageResult<T> toPageResult(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }
}
