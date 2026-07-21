package com.lingxi.workbench.admin.knowledge.service.event;

/** 数据库事务提交后触发的文档解析事件。 */
public record KnowledgeParseRequestedEvent(Long jobId) {}
