package com.lingxi.workbench.admin.intent.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeEntity;
import com.lingxi.workbench.admin.intent.model.request.IntentTreePageQuery;
import com.lingxi.workbench.admin.intent.repo.mapper.IntentTreeMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** 意图树主记录数据仓库。 */
@Repository
@RequiredArgsConstructor
public class IntentTreeRepo {

    private final IntentTreeMapper mapper;

    /** 按关键字和状态分页查询意图树。 */
    public PageResult<IntentTreeEntity> findPage(IntentTreePageQuery query) {
        IPage<IntentTreeEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<IntentTreeEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(IntentTreeEntity::getName, query.getKeyword())
                                .or()
                                .like(IntentTreeEntity::getCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getStatus()), IntentTreeEntity::getStatus, query.getStatus())
                        .orderByDesc(IntentTreeEntity::getUpdatedAt)
                        .orderByDesc(IntentTreeEntity::getId));
        return PageResult.convert(page);
    }

    public Optional<IntentTreeEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 带行锁查询意图树，用于发布和切换版本。 */
    public Optional<IntentTreeEntity> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<IntentTreeEntity>lambdaQuery()
                        .eq(IntentTreeEntity::getId, id)
                        .last("FOR UPDATE")));
    }

    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<IntentTreeEntity>lambdaQuery()
                .eq(IntentTreeEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), IntentTreeEntity::getId, excludedId));
    }

    public void save(IntentTreeEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }
}
