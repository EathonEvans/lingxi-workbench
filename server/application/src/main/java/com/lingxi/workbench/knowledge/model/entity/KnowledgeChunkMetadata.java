package com.lingxi.workbench.knowledge.model.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chunk JSONB 元数据（只读，字符串化字段避免跨模块枚举依赖）。
 */
@Data
@NoArgsConstructor
public class KnowledgeChunkMetadata {
    /** 文档名称。 */
    private String documentName;
    /** 原始文件名。 */
    private String fileName;
    /** 文件类型。 */
    private String fileType;
    /** 文档来源类型。 */
    private String sourceType;
    /** 分块策略。 */
    private String strategy;
    /** 所属章节标题。 */
    private String sectionTitle;
    /** 当前页号。 */
    private Integer pageNumber;
    /** 文档总页数。 */
    private Integer pageCount;
    /** 在原文中的起始字符偏移。 */
    private Integer charStart;
    /** 在原文中的结束字符偏移。 */
    private Integer charEnd;
}
