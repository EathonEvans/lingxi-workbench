package com.lingxi.workbench.knowledge.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索结果 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResultVO {
    /** Chunk ID。 */
    private Long chunkId;
    /** Chunk 文本内容。 */
    private String content;
    /** 余弦相似度（0~1），越高越相关。 */
    private Double similarity;
    /** 文档名称。 */
    private String documentName;
    /** 所属章节路径。 */
    private String sectionTitle;
    /** 页码。 */
    private Integer pageNumber;
}
