package com.lingxi.workbench.admin.application.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.request.ApplicationPageQuery;
import com.lingxi.workbench.admin.application.repo.mapper.AiApplicationMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** Application 主记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class AiApplicationRepo {
    private final AiApplicationMapper mapper;

    /** 按关键字和状态分页查询 Application 列表。 */
    public PageResult<AiApplicationEntity> findPage(ApplicationPageQuery query) {
        IPage<AiApplicationEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<AiApplicationEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(AiApplicationEntity::getName, query.getKeyword())
                                .or()
                                .like(AiApplicationEntity::getCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getStatus()),
                                AiApplicationEntity::getStatus, query.getStatus())
                        .orderByDesc(AiApplicationEntity::getUpdatedAt)
                        .orderByDesc(AiApplicationEntity::getId));
        return PageResult.convert(page);
    }

    /** 按主键查询 Application。 */
    public Optional<AiApplicationEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 带行锁按主键查询 Application，用于发布等写操作。 */
    public Optional<AiApplicationEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AiApplicationEntity>lambdaQuery()
                        .eq(AiApplicationEntity::getId, id)
                        .last("FOR UPDATE")));
    }

    /** 校验 Application 编码唯一性，支持排除指定记录。 */
    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<AiApplicationEntity>lambdaQuery()
                .eq(AiApplicationEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), AiApplicationEntity::getId, excludedId));
    }

    public void save(AiApplicationEntity entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
