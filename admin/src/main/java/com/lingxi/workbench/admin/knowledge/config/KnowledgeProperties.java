package com.lingxi.workbench.admin.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 知识库文档存储与解析配置。 */
@ConfigurationProperties(prefix = "lingxi.knowledge")
public record KnowledgeProperties(Storage storage, Parsing parsing) {
    public record Storage(String endpoint, String accessKey, String secretKey, String bucket) {}
    public record Parsing(long maxFileSize, int maxCharacters) {}
}
