package com.lingxi.workbench.admin.knowledge.service.event;

/** 文档记录删除事务提交后，清理对象存储中的原始文件。 */
public record KnowledgeObjectDeleteRequestedEvent(String objectKey) {
}
