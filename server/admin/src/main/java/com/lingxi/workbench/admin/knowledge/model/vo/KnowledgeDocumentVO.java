package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 知识库文档响应，不包含可能较大的解析正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeDocumentVO {
    /**
     * 文档ID
     */
    private String id;
    /**
     * 知识库ID
     */
    private String knowledgeBaseId;
    /**
     * 文档名称
     */
    private String name;
    /**
     * 文档来源类型
     */
    private KnowledgeDocumentSourceType sourceType;
    /**
     * 原始文件名
     */
    private String fileName;
    /**
     * 文件类型
     */
    private String fileType;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 文档处理状态
     */
    private KnowledgeDocumentStatus status;
    /**
     * 文档总页数
     */
    private Integer pageCount;
    /**
     * 分块策略
     */
    private KnowledgeChunkStrategy chunkStrategy;
    /**
     * Chunk大小
     */
    private Integer chunkSize;
    /**
     * Chunk重叠大小
     */
    private Integer chunkOverlap;
    /**
     * Chunk总数
     */
    private Integer chunkCount;
    /**
     * 失败信息
     */
    private String errorMessage;
    /**
     * 当前生效的分块任务ID
     */
    private String activeChunkTaskId;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
