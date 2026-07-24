package com.lingxi.workbench.admin.knowledge.event.event;

/**
 * 本地事务提交后投递分块任务的领域事件。
 *
 * @param taskId 分块任务ID
 */
public record KnowledgeChunkRequestedEvent(Long taskId) {
}
