package com.lingxi.workbench.admin.knowledge.consumer;

/**
 * 文档解析完成事件，由生产者投递至 RocketMQ。
 *
 * @param documentId 文档 ID
 * @param operatorId 操作人 ID
 */
public record KnowledgeDocumentParsedEvent(
        Long documentId,
        Long operatorId) {
}
