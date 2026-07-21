package com.lingxi.workbench.admin.knowledge.config;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 知识库文档存储与解析配置。 */
@ConfigurationProperties(prefix = "lingxi.knowledge")
public record KnowledgeProperties(Storage storage, Parsing parsing, Chunking chunking) {
    public record Storage(String endpoint, String accessKey, String secretKey, String bucket) {}
    public record Parsing(long maxFileSize, int maxCharacters) {}
    public record Chunking(KnowledgeChunkStrategy strategy, int chunkSize, int chunkOverlap) {}
}
