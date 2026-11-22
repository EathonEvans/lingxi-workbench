package com.lingxi.workbench.admin.knowledge.event.event;

/**
 * 原始文件已写入对象存储，用于在数据库事务回滚时执行补偿删除。
 *
 * @param objectKey 对象存储中的文件Key
 */
public record KnowledgeObjectStoredEvent(String objectKey) {
}
