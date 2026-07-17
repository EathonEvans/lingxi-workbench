package com.lingxi.workbench.admin.prompt.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.repo.mapper.PromptMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** Prompt主记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class PromptRepo {

    private final PromptMapper mapper;

    /** 分页查询Prompt。 */
    public PageResult<PromptEntity> findPage(PromptPageQuery query) {
        IPage<PromptEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<PromptEntity>lambdaQuery()
                        .like(CharSequenceUtil.isNotBlank(query.getName()), PromptEntity::getName, query.getName())
                        .like(CharSequenceUtil.isNotBlank(query.getCode()), PromptEntity::getCode, query.getCode())
                        .eq(Objects.nonNull(query.getRole()), PromptEntity::getRole, query.getRole())
                        .eq(Objects.nonNull(query.getStatus()), PromptEntity::getStatus, query.getStatus())
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
                .ne(Objects.nonNull(excludedId), PromptEntity::getId, excludedId));
    }

    public void save(PromptEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
