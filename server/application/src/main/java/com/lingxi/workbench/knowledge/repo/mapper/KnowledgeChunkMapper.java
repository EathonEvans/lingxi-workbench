package com.lingxi.workbench.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Chunk 只读数据访问。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {
}
