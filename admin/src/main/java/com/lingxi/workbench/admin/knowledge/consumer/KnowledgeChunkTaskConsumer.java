package com.lingxi.workbench.admin.knowledge.consumer;

import com.lingxi.workbench.admin.common.persistence.KnowledgeMqTopics;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.message.KnowledgeChunkTaskMessage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentParser;
import com.lingxi.workbench.admin.knowledge.service.impl.KnowledgeChunkPipelineService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.lingxi.workbench.framework.common.enums.HttpStatusEnum.INTERNAL_SERVER_ERROR;

/**
 * 在单个异步消费任务中执行文档下载、解析、分块、向量化和最终入库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = KnowledgeMqTopics.CHUNK_EXECUTE,
        consumerGroup = "knowledge-chunk-execute-group")
public class KnowledgeChunkTaskConsumer implements RocketMQListener<KnowledgeChunkTaskMessage> {
    private final KnowledgeChunkPipelineService pipelineService;

    @Value("${lingxi.knowledge.embedding.batch-size:32}")
    private int batchSize;

    @Override
    public void onMessage(KnowledgeChunkTaskMessage message) {
        Long taskId = message.taskId();
        try {
            pipelineService.startProcessing(taskId).ifPresent(processingInput -> {
                KnowledgeDocumentParser.ParsedDocument parsedDocument;
                try {
                    parsedDocument = pipelineService.parse(processingInput);
                } catch (Exception e) {
                    throw new BusinessException(INTERNAL_SERVER_ERROR, "document fetch fail");
                }
                KnowledgeChunkPipelineService.ChunkInput chunkInput = pipelineService.startChunking(processingInput, parsedDocument);
                // 执行分块
                List<KnowledgeChunkBO> chunks = pipelineService.chunk(chunkInput);
                // 开始向量化
                KnowledgeChunkPipelineService.VectorInput vectorInput = pipelineService.startVectorize(chunkInput, chunks);
                List<float[]> vectors = embedInBatches(vectorInput);
                pipelineService.completeProcessing(vectorInput, vectors);
            });
        } catch (Exception exception) {
            log.error("知识库文档处理失败，taskId={}", taskId, exception);
            boolean retrying = pipelineService.handleFailure(taskId, KnowledgeChunkTaskStatus.PENDING,
                    "DOCUMENT_PROCESS_FAILED", exception);
            if (retrying) {
                throw new IllegalStateException("文档处理失败，等待RocketMQ重试", exception);
            }
        }
    }

    /**
     * Embedding调用按批次执行，向量暂存在消费线程内；
     * 所有批次成功后再由流水线服务统一提交数据库。
     */
    private List<float[]> embedInBatches(KnowledgeChunkPipelineService.VectorInput input) {
        int effectiveBatchSize = Math.max(1, batchSize);
        List<float[]> vectors = new ArrayList<>(input.chunks().size());
        for (int start = 0; start < input.chunks().size(); start += effectiveBatchSize) {
            int end = Math.min(start + effectiveBatchSize, input.chunks().size());
            List<KnowledgeChunkEntity> batch = List.copyOf(input.chunks().subList(start, end));
            vectors.addAll(pipelineService.embed(batch, input.modelConfig()));
            pipelineService.updateVectorizeProgress(input, end);
        }
        return vectors;
    }
}
