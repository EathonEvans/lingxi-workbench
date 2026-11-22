package com.lingxi.workbench.admin.knowledge.model.entity;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 随Chunk持久化的检索上下文元数据。
 *
 * <p>关系主键和过滤字段继续保存在Chunk表普通列中；这里保存引用来源、结构位置和字符区间，
 * 后续可在不改表结构的情况下扩展页码、章节层级等解析信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeChunkMetadata {
    /**
     * 文档名称
     */
    private String documentName;
    /**
     * 原始文件名
     */
    private String fileName;
    /**
     * 文件类型
     */
    private String fileType;
    /**
     * 文档来源类型
     */
    private KnowledgeDocumentSourceType sourceType;
    /**
     * 分块策略
     */
    private KnowledgeChunkStrategy strategy;
    /**
     * 所属章节标题
     */
    private String sectionTitle;
    /**
     * 当前页号
     */
    private Integer pageNumber;
    /**
     * 文档总页数
     */
    private Integer pageCount;
    /**
     * 在原文中的起始字符偏移
     */
    private Integer charStart;
    /**
     * 在原文中的结束字符偏移
     */
    private Integer charEnd;
}
