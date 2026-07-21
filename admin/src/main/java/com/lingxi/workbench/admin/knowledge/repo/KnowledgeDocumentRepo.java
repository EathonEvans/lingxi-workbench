package com.lingxi.workbench.admin.knowledge.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeDocumentMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/** 知识库文档数据仓库。 */
@Repository
@RequiredArgsConstructor
public class KnowledgeDocumentRepo {
    private final KnowledgeDocumentMapper mapper;

    public PageResult<KnowledgeDocumentEntity> findPage(KnowledgeDocumentPageQuery query) {
        IPage<KnowledgeDocumentEntity> page = mapper.selectPage(query.toPage(),
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, query.getKnowledgeBaseId())
                        .like(CharSequenceUtil.isNotBlank(query.getKeyword()), KnowledgeDocumentEntity::getName, query.getKeyword())
                        .eq(Objects.nonNull(query.getStatus()), KnowledgeDocumentEntity::getStatus, query.getStatus())
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt));
        return PageResult.convert(page);
    }

    public Optional<KnowledgeDocumentEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByBaseId(Long knowledgeBaseId) {
        return mapper.exists(Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId));
    }

    public boolean existsByHash(Long knowledgeBaseId, String contentHash) {
        return mapper.exists(Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocumentEntity::getContentHash, contentHash));
    }

    public void save(KnowledgeDocumentEntity entity) {
        if (Objects.isNull(entity.getId())) mapper.insert(entity);
        else mapper.updateById(entity);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
