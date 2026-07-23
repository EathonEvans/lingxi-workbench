package com.lingxi.workbench.admin.common.persistence;

/**
 * 知识库异步处理使用的RocketMQ主题。
 */
public interface KnowledgeMqTopics {

    String CHUNK_EXECUTE = "knowledge-chunk-execute-topic";
}
