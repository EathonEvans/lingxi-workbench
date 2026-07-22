package com.lingxi.workbench.admin.knowledge.consumer;

import com.lingxi.workbench.admin.knowledge.service.KnowledgeChunkService;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 知识库文档解析完成后的异步处理消费者示例。
 *
 * <p>消费流程：接收文档解析完成事件 → 执行分块 → 调用 Embedding 向量化 → 存入 pgvector。
 * 当前为示例骨架，接入实际的 Embedding 服务和向量存储后替换 TODO 标记部分。</p>
 *
 * <p>事务消息配合方式：生产者发送事务半消息，本地事务中更新文档状态为"处理中"并提交，
 * 消费者始终能拿到已标记处理中的文档，不会重复消费。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "knowledge-document-parsed-topic",
        consumerGroup = "knowledge-document-process-group"
)
@RequiredArgsConstructor
public class KnowledgeDocumentParsedConsumer implements RocketMQListener<KnowledgeDocumentParsedEvent> {


    private final KnowledgeDocumentService documentService;
    private final KnowledgeChunkService chunkService;



    /**
     * 接收文档解析完成事件，异步执行分块和向量化流程。
     */
    @Override
    public void onMessage(KnowledgeDocumentParsedEvent event) {
        log.info("收到文档解析完成事件: documentId={}", event.documentId());

        // 1. 执行文档分块
        // TODO 从配置或上次执行参数中读取 chunk 策略
        // ExecuteKnowledgeChunkRequest request = ...;
        // chunkService.execute(event.documentId(), request, event.operatorId());

        // 2. 对每个 Chunk 调用 Embedding 模型进行向量化
        // TODO 调用 Embedding 服务，获取每个 Chunk 的向量表示
        // List<float[]> vectors = embeddingService.embed(chunks);

        // 3. 将 Chunk + 向量存入 pgvector
        // TODO 批量写入向量数据库
        // vectorStore.insert(event.documentId(), chunks, vectors);

        // 4. 更新文档状态为"已完成"
        // TODO documentService.markProcessed(event.documentId());

        log.info("文档解析后处理完成: documentId={}", event.documentId());
    }
}
