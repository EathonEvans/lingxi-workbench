package com.lingxi.workbench.admin.model.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.ModelProviderPageQuery;
import com.lingxi.workbench.admin.model.repo.mapper.ModelProviderMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 模型供应商数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelProviderRepo {

    private final ModelProviderMapper mapper;

    /** 分页查询模型供应商。 */
    public PageResult<ModelProviderEntity> findPage(ModelProviderPageQuery query) {
        IPage<ModelProviderEntity> page = mapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<ModelProviderEntity>lambdaQuery()
                        .like(hasText(query.getName()), ModelProviderEntity::getName, trim(query.getName()))
                        .like(hasText(query.getCode()), ModelProviderEntity::getCode, trim(query.getCode()))
                        .eq(query.getStatus() != null, ModelProviderEntity::getStatus, query.getStatus())
                        .orderByAsc(ModelProviderEntity::getId));
        return PageResult.convert(page);
    }

    /** 查询全部模型供应商，供关联配置选择。 */
    public List<ModelProviderEntity> findAll() {
        return mapper.selectList(Wrappers.<ModelProviderEntity>lambdaQuery()
                .orderByAsc(ModelProviderEntity::getId));
    }

    public Optional<ModelProviderEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<ModelProviderEntity>lambdaQuery()
                .eq(ModelProviderEntity::getCode, code)
                .ne(excludedId != null, ModelProviderEntity::getId, excludedId));
    }

    public void save(ModelProviderEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
