package com.lingxi.workbench.admin.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 知识库文档存储与解析配置。 */
@ConfigurationProperties(prefix = "lingxi.knowledge")
public record KnowledgeProperties(Parsing parsing) {
    public record Parsing(long maxFileSize, int maxCharacters) {}
}
