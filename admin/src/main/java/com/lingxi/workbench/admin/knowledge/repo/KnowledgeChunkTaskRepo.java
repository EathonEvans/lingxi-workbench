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
 *
 * <p>管理分块任务的全生命周期：查询、版本号生成、状态流转和补偿恢复。</p>
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkTaskRepo {
    private final KnowledgeChunkTaskMapper mapper;

    /** 按主键查询任务。 */
    public Optional<KnowledgeChunkTaskEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按文档 ID 查询最新版本的任务。 */
    public Optional<KnowledgeChunkTaskEntity> findLatestByDocumentId(Long documentId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<KnowledgeChunkTaskEntity>lambdaQuery()
                .eq(KnowledgeChunkTaskEntity::getDocumentId, documentId)
                .orderByDesc(KnowledgeChunkTaskEntity::getVersionNo)
                .last("LIMIT 1")));
    }

    /** 检查文档是否存在活跃（非终态）任务。 */
    public boolean existsActive(Long documentId) {
        return mapper.exists(Wrappers.<KnowledgeChunkTaskEntity>lambdaQuery()
                .eq(KnowledgeChunkTaskEntity::getDocumentId, documentId)
                .in(KnowledgeChunkTaskEntity::getStatus,
                        KnowledgeChunkTaskStatus.PENDING,
                        KnowledgeChunkTaskStatus.PARSING,
                        KnowledgeChunkTaskStatus.CHUNKING,
                        KnowledgeChunkTaskStatus.VECTORIZING));
    }

    /** 获取文档下一版任务版本号。 */
    public int nextVersion(Long documentId) {
        return mapper.nextVersion(documentId);
    }

    /** 原子抢占解析阶段，仅 PENDING 任务可被抢占。 */
    public boolean claimParsing(Long id, OffsetDateTime startedAt) {
        return mapper.claimParsing(id, startedAt) == 1;
    }

    /** 原子推进到分块阶段，仅 PARSING 任务可推进。 */
    public boolean markChunking(Long id) {
        return mapper.markChunking(id) == 1;
    }

    /** 原子推进到向量化阶段，仅 CHUNKING 任务可推进。 */
    public boolean markVectorizing(Long id) {
        return mapper.markVectorizing(id) == 1;
    }

    /** 按状态查询任务 ID 列表，用于补偿投递。 */
    public List<Long> findIdsByStatus(KnowledgeChunkTaskStatus status) {
        return mapper.findIdsByStatus(status.getCode());
    }

    /** 将超过期限的执行中任务重置为 PENDING 状态，用于服务重启后恢复。 */
    public void resetStale(OffsetDateTime deadline) {
        mapper.resetStale(deadline);
    }

    /** 新增或更新任务，ID 为空时插入否则更新。 */
    public void upsert(KnowledgeChunkTaskEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /** 按文档 ID 删除全部处理任务。 */
    public void deleteByDocumentId(Long documentId) {
        mapper.delete(Wrappers.<KnowledgeChunkTaskEntity>lambdaQuery()
                .eq(KnowledgeChunkTaskEntity::getDocumentId, documentId));
    }
}
