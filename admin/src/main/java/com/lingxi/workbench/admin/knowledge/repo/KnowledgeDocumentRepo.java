package com.lingxi.workbench.admin.knowledge.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeDocumentMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * 知识库文档数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeDocumentRepo {
    private final KnowledgeDocumentMapper mapper;

    /**
     * 按知识库ID、关键字和状态分页查询文档，按创建时间降序排列。
     */
    public PageResult<KnowledgeDocumentEntity> findPage(KnowledgeDocumentPageQuery query) {
        IPage<KnowledgeDocumentEntity> page = mapper.selectPage(query.toPage(),
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, query.getKnowledgeBaseId())
                        .like(CharSequenceUtil.isNotBlank(query.getKeyword()), KnowledgeDocumentEntity::getName, query.getKeyword())
                        .eq(Objects.nonNull(query.getStatus()), KnowledgeDocumentEntity::getStatus, query.getStatus())
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt));
        return PageResult.convert(page);
    }

    /**
     * 按主键查询文档。
     */
    public Optional<KnowledgeDocumentEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /**
     * 检查知识库下是否存在文档。
     */
    public boolean existsByBaseId(Long knowledgeBaseId) {
        return mapper.exists(Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId));
    }

    /**
     * 检查知识库下是否已存在相同内容哈希的文档。
     */
    public boolean existsByHash(Long knowledgeBaseId, String contentHash) {
        return mapper.exists(Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocumentEntity::getContentHash, contentHash));
    }

    /**
     * 原子标记文档为处理中，同时持久化本次分块配置。
     */
    public boolean claimProcessing(Long id, KnowledgeChunkStrategy strategy,
                                   Integer chunkSize, Integer chunkOverlap, Long operatorId) {
        return mapper.claimProcessing(id, strategy.getCode(), chunkSize, chunkOverlap, operatorId) == 1;
    }

    /**
     * 新增或更新文档，ID为空时插入否则更新。
     */
    public void upsert(KnowledgeDocumentEntity entity) {
        if (Objects.isNull(entity.getId())){
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /**
     * 按主键删除文档。
     */
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
