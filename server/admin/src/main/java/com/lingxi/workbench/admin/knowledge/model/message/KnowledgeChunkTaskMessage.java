package com.lingxi.workbench.admin.knowledge.model.message;

/**
 * 分块任务消息，消息体只携带稳定业务主键。
 *
 * @param taskId 分块任务ID
 */
public record KnowledgeChunkTaskMessage(Long taskId) {
}
