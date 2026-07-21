package com.lingxi.workbench.admin.knowledge.service.event;

/** 原始文件已写入对象存储，用于在数据库事务回滚时执行补偿删除。 */
public record KnowledgeObjectStoredEvent(String objectKey) {
}
