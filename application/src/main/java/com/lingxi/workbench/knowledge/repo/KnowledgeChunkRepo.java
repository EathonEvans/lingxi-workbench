package com.lingxi.workbench.knowledge.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.knowledge.repo.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Chunk 只读数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkRepo {

    private final KnowledgeChunkMapper mapper;

    /**
     * 批量按 ID 查询已启用且非空的 Chunk。
     */
    public List<KnowledgeChunkEntity> findEnabledByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .in(KnowledgeChunkEntity::getId, ids)
                .eq(KnowledgeChunkEntity::getEnabled, true));
    }
}
