package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import java.time.OffsetDateTime;

/** 知识库文档响应，不包含可能较大的解析正文。 */
public record KnowledgeDocumentVO(String id, String knowledgeBaseId, String name,
                                  KnowledgeDocumentSourceType sourceType, String fileName,
                                  String fileType, Long fileSize, KnowledgeDocumentStatus status,
                                  Integer pageCount, KnowledgeChunkStrategy chunkStrategy,
                                  Integer chunkSize, Integer chunkOverlap, Integer chunkCount,
                                  String errorMessage, String activeChunkTaskId,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
