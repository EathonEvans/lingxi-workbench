package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkMetadata;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个文档Chunk预览。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeChunkVO {
    /**
     * Chunk ID
     */
    private String id;
    /**
     * Chunk顺序号
     */
    private int chunkNo;
    /**
     * Chunk正文
     */
    private String content;
    /**
     * 字符数量
     */
    private int charCount;
    /**
     * Token数量
     */
    private int tokenCount;
    /**
     * 检索上下文元数据
     */
    private KnowledgeChunkMetadata metadata;
}
