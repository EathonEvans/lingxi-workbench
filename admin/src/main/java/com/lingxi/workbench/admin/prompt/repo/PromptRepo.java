package com.lingxi.workbench.admin.prompt.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.repo.mapper.PromptMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Prompt 主记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class PromptRepo {

    private final PromptMapper mapper;

    /** 分页查询 Prompt。 */
    public PageResult<PromptEntity> findPage(PromptPageQuery query) {
        IPage<PromptEntity> page = mapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                Wrappers.<PromptEntity>lambdaQuery()
                        .like(hasText(query.getName()), PromptEntity::getName, trim(query.getName()))
                        .like(hasText(query.getCode()), PromptEntity::getCode, trim(query.getCode()))
                        .eq(query.getStatus() != null, PromptEntity::getStatus, query.getStatus())
                        .orderByDesc(PromptEntity::getUpdatedAt)
                        .orderByDesc(PromptEntity::getId));
        return PageResult.convert(page);
    }

    public Optional<PromptEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<PromptEntity>lambdaQuery()
                .eq(PromptEntity::getCode, code)
                .ne(excludedId != null, PromptEntity::getId, excludedId));
    }

    public void save(PromptEntity entity) {
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
