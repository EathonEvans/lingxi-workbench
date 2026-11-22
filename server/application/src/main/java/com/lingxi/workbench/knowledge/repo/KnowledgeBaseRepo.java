package com.lingxi.workbench.knowledge.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.knowledge.repo.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 知识库只读数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepo {

    private final KnowledgeBaseMapper mapper;

    /**
     * 按 ID 查询已启用的知识库。
     */
    public Optional<KnowledgeBaseEntity> findActiveById(Long id) {
        KnowledgeBaseEntity entity = mapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getId, id)
                .eq(KnowledgeBaseEntity::getStatus, "ACTIVE"));
        return Optional.ofNullable(entity);
    }
    public boolean canRead(Long knowledgeBaseId, Long userId) {
        return mapper.canRead(knowledgeBaseId, userId);
    }

    public Optional<Integer> findIndexDimension(Long knowledgeBaseId, Long indexVersionId, Long modelId) {
        return Optional.ofNullable(mapper.selectIndexDimension(knowledgeBaseId, indexVersionId, modelId));
    }
}
