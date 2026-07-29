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
 * 文档 Chunk 数据仓库。
 *
 * <p>负责 Chunk 的读写、按任务批量替换和按文档级联删除。</p>
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkRepo {
    private final KnowledgeChunkMapper mapper;

    /** 按主键查询 Chunk。 */
    public Optional<KnowledgeChunkEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 原子替换指定任务的全部 Chunk：先删后批量写入。 */
    public void replaceTaskChunks(Long taskId, List<KnowledgeChunkEntity> chunks) {
        mapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery().eq(KnowledgeChunkEntity::getTaskId, taskId));
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        mapper.insertBatch(chunks);
    }

    /** 按任务 ID 查询全部 Chunk，按顺序号升序排列。 */
    public List<KnowledgeChunkEntity> findByTaskId(Long taskId) {
        return mapper.selectList(Wrappers.<KnowledgeChunkEntity>lambdaQuery().eq(KnowledgeChunkEntity::getTaskId, taskId).orderByAsc(KnowledgeChunkEntity::getChunkNo));
    }

    /** 按任务 ID 分页查询 Chunk，按顺序号升序排列。 */
    public PageResult<KnowledgeChunkEntity> findPage(KnowledgeChunkPageQuery query) {
        IPage<KnowledgeChunkEntity> page = mapper.selectPage(query.toPage(), Wrappers.<KnowledgeChunkEntity>lambdaQuery().eq(KnowledgeChunkEntity::getTaskId, query.getTaskId()).orderByAsc(KnowledgeChunkEntity::getChunkNo));
        return PageResult.convert(page);
    }

    /** 新增或更新 Chunk，ID 为空时插入否则更新。 */
    public void upsert(KnowledgeChunkEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    /** 按文档 ID 删除全部 Chunk。 */
    public void deleteByDocumentId(Long documentId) {
        mapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId));
    }
}
