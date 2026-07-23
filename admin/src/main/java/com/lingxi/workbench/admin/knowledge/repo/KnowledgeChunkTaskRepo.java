package com.lingxi.workbench.admin.knowledge.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeChunkTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 文档分块任务数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkTaskRepo {
    private final KnowledgeChunkTaskMapper mapper;

    public Optional<KnowledgeChunkTaskEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<KnowledgeChunkTaskEntity> findLatestByDocumentId(Long documentId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<KnowledgeChunkTaskEntity>lambdaQuery()
                .eq(KnowledgeChunkTaskEntity::getDocumentId, documentId)
                .orderByDesc(KnowledgeChunkTaskEntity::getVersionNo)
                .last("LIMIT 1")));
    }

    public boolean existsActive(Long documentId) {
        return mapper.exists(Wrappers.<KnowledgeChunkTaskEntity>lambdaQuery()
                .eq(KnowledgeChunkTaskEntity::getDocumentId, documentId)
                .in(KnowledgeChunkTaskEntity::getStatus,
                        KnowledgeChunkTaskStatus.PENDING,
                        KnowledgeChunkTaskStatus.PARSING,
                        KnowledgeChunkTaskStatus.CHUNKING,
                        KnowledgeChunkTaskStatus.VECTORIZING));
    }

    public int nextVersion(Long documentId) {
        return mapper.nextVersion(documentId);
    }

    public boolean claimParsing(Long id, OffsetDateTime startedAt) {
        return mapper.claimParsing(id, startedAt) == 1;
    }

    public boolean markChunking(Long id) {
        return mapper.markChunking(id) == 1;
    }

    public boolean markVectorizing(Long id) {
        return mapper.markVectorizing(id) == 1;
    }

    public List<Long> findIdsByStatus(KnowledgeChunkTaskStatus status) {
        return mapper.findIdsByStatus(status.getCode());
    }

    public void resetStale(OffsetDateTime deadline) {
        mapper.resetStale(deadline);
    }

    public void upsert(KnowledgeChunkTaskEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /**
     * 按文档删除全部处理任务。
     */
    public void deleteByDocumentId(Long documentId) {
        mapper.delete(Wrappers.<KnowledgeChunkTaskEntity>lambdaQuery()
                .eq(KnowledgeChunkTaskEntity::getDocumentId, documentId));
    }
}
