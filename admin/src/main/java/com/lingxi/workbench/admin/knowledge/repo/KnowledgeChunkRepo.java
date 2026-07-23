package com.lingxi.workbench.admin.knowledge.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeChunkPageQuery;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeChunkMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 文档Chunk数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkRepo {
    private final KnowledgeChunkMapper mapper;

    public Optional<KnowledgeChunkEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public void replaceTaskChunks(Long taskId, List<KnowledgeChunkEntity> chunks) {
        mapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery().eq(KnowledgeChunkEntity::getTaskId, taskId));
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        mapper.insertBatch(chunks);
    }

    public List<KnowledgeChunkEntity> findByTaskId(Long taskId) {
        return mapper.selectList(Wrappers.<KnowledgeChunkEntity>lambdaQuery().eq(KnowledgeChunkEntity::getTaskId, taskId).orderByAsc(KnowledgeChunkEntity::getChunkNo));
    }

    public PageResult<KnowledgeChunkEntity> findPage(KnowledgeChunkPageQuery query) {
        IPage<KnowledgeChunkEntity> page = mapper.selectPage(query.toPage(), Wrappers.<KnowledgeChunkEntity>lambdaQuery().eq(KnowledgeChunkEntity::getTaskId, query.getTaskId()).orderByAsc(KnowledgeChunkEntity::getChunkNo));
        return PageResult.convert(page);
    }

    public void upsert(KnowledgeChunkEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /** 按文档删除全部Chunk。 */
    public void deleteByDocumentId(Long documentId) {
        mapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId));
    }
}
