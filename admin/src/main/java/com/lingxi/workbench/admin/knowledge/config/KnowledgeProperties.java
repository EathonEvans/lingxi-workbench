package com.lingxi.workbench.admin.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库文档存储与解析配置。
 *
 * @param parsing 文档解析配置
 */
@ConfigurationProperties(prefix = "lingxi.knowledge")
public record KnowledgeProperties(Parsing parsing) {
    /**
     * 文档解析相关限制。
     *
     * @param maxFileSize   最大文件字节数
     * @param maxCharacters 最大解析字符数
     */
    public record Parsing(long maxFileSize, int maxCharacters) {}
}
