package com.lingxi.workbench.admin.knowledge.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeBaseMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** 知识库数据仓库。 */
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepo {
    private final KnowledgeBaseMapper mapper;

    public PageResult<KnowledgeBaseEntity> findPage(KnowledgeBasePageQuery query) {
        IPage<KnowledgeBaseEntity> page = mapper.selectPage(query.toPage(),
                Wrappers.<KnowledgeBaseEntity>lambdaQuery()
                        .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                                .like(KnowledgeBaseEntity::getName, query.getKeyword())
                                .or().like(KnowledgeBaseEntity::getCode, query.getKeyword()))
                        .eq(Objects.nonNull(query.getStatus()), KnowledgeBaseEntity::getStatus, query.getStatus())
                        .orderByDesc(KnowledgeBaseEntity::getUpdatedAt));
        return PageResult.convert(page);
    }

    public Optional<KnowledgeBaseEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByCode(String code, Long excludedId) {
        return mapper.exists(Wrappers.<KnowledgeBaseEntity>lambdaQuery()
                .eq(KnowledgeBaseEntity::getCode, code)
                .ne(Objects.nonNull(excludedId), KnowledgeBaseEntity::getId, excludedId));
    }

    public void save(KnowledgeBaseEntity entity) {
        if (Objects.isNull(entity.getId())) mapper.insert(entity);
        else mapper.updateById(entity);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
