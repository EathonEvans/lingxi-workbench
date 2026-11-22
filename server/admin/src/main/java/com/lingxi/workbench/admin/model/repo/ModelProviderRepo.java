package com.lingxi.workbench.admin.model.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.ModelProviderPageQuery;
import com.lingxi.workbench.admin.model.repo.mapper.ModelProviderMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 模型供应商数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelProviderRepo {

    private final ModelProviderMapper mapper;

    /** 分页查询模型供应商。 */
    public PageResult<ModelProviderEntity> findPage(ModelProviderPageQuery query) {
        IPage<ModelProviderEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<ModelProviderEntity>lambdaQuery()
                        .like(CharSequenceUtil.isNotBlank(query.getName()), ModelProviderEntity::getName, query.getName())
                        .like(CharSequenceUtil.isNotBlank(query.getCode()), ModelProviderEntity::getCode, query.getCode())
                        .eq(Objects.nonNull(query.getStatus()), ModelProviderEntity::getStatus, query.getStatus())
                        .orderByAsc(ModelProviderEntity::getId));
        return PageResult.convert(page);
    }

    /** 查询模型供应商列表（上限 1000 防止字典表失控），供关联配置选择。 */
    public List<ModelProviderEntity> findAll() {
        return mapper.selectList(Wrappers.<ModelProviderEntity>lambdaQuery()
                .orderByAsc(ModelProviderEntity::getId)
                .last("LIMIT 1000"));
    }

    public Optional<ModelProviderEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 检查编码是否已存在，排除指定 id 用于更新时校验。 */
    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<ModelProviderEntity>lambdaQuery()
                .eq(ModelProviderEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), ModelProviderEntity::getId, excludedId));
    }

    /** 新增或更新供应商，id 为空时插入，否则按主键更新。 */
    public void save(ModelProviderEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

}
