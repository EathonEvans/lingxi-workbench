package com.lingxi.workbench.admin.knowledge.event.event;

/**
 * 文档记录删除事务提交后，清理对象存储中的原始文件。
 *
 * @param objectKey 对象存储中的文件Key
 */
public record KnowledgeObjectDeleteRequestedEvent(String objectKey) {
}
