package com.lingxi.workbench.admin.knowledge.model.entity;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;

/**
 * 随Chunk持久化的检索上下文元数据。
 *
 * <p>关系主键和过滤字段继续保存在Chunk表普通列中；这里保存引用来源、结构位置和字符区间，
 * 后续可在不改表结构的情况下扩展页码、章节层级等解析信息。</p>
 */
public record KnowledgeChunkMetadata(
        String documentName,
        String fileName,
        String fileType,
        KnowledgeDocumentSourceType sourceType,
        KnowledgeChunkStrategy strategy,
        String sectionTitle,
        Integer pageNumber,
        Integer pageCount,
        Integer charStart,
        Integer charEnd) {
}
