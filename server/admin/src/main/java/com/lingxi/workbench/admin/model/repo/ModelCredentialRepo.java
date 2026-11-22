package com.lingxi.workbench.admin.model.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.request.ModelCredentialPageQuery;
import com.lingxi.workbench.admin.model.repo.mapper.ModelCredentialMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 模型访问密钥数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class ModelCredentialRepo {

    private final ModelCredentialMapper mapper;

    /**
     * 分页查询模型访问密钥。
     */
    public PageResult<ModelCredentialEntity> findPage(ModelCredentialPageQuery query) {
        IPage<ModelCredentialEntity> page = mapper.selectPage(
                query.toPage(), Wrappers.<ModelCredentialEntity>lambdaQuery()
                        .like(CharSequenceUtil.isNotBlank(query.getName()), ModelCredentialEntity::getName, query.getName())
                        .eq(Objects.nonNull(query.getProviderId()), ModelCredentialEntity::getProviderId, query.getProviderId())
                        .eq(Objects.nonNull(query.getStatus()), ModelCredentialEntity::getStatus, query.getStatus())
                        .orderByAsc(ModelCredentialEntity::getId));
        return PageResult.convert(page);
    }

    /**
     * 查询模型访问密钥列表（上限 1000 防止字典表失控），供模型实例选择。
     */
    public List<ModelCredentialEntity> findAll() {
        return mapper.selectList(Wrappers.<ModelCredentialEntity>lambdaQuery()
                .orderByAsc(ModelCredentialEntity::getId)
                .last("LIMIT 1000"));
    }

    public Optional<ModelCredentialEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 检查同一供应商下是否已存在同名密钥。 */
    public boolean existsByProviderIdAndName(Long providerId, String name) {
        return mapper.exists(Wrappers.<ModelCredentialEntity>lambdaQuery()
                .eq(ModelCredentialEntity::getProviderId, providerId)
                .eq(ModelCredentialEntity::getName, name));
    }

    /** 新增或更新密钥，id 为空时插入，否则按主键更新。 */
    public void save(ModelCredentialEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

}
