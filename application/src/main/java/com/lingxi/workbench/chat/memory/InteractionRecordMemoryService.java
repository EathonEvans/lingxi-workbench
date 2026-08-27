package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.chat.memory.config.MemoryEmbeddingModelResolver;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.memory.model.MemoryData;
import com.lingxi.workbench.chat.memory.model.bo.InteractionMemoryWrite;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 提取关键交互记录，并在外部调用完成后原子写入记录和向量。 */
@Service
@RequiredArgsConstructor
public class InteractionRecordMemoryService {

    private final MemoryCompletionService completionService;
    private final MemoryEmbeddingModelResolver embeddingResolver;
    private final EmbeddingGateway embeddingGateway;
    private final InteractionMemoryStoreService storeService;

    /**
     * 提取关键交互记录并向量化存储。
     * 同一 Assistant 消息只会提取一次（防重），提取结果含 summary 和 entities，
     * summary 向量化后写入 pgvector，供后续对话做语义检索。
     *
     * @return 写入后的记录主键
     */
    public Long extract(AssistantCompletedEvent event, String history, MemoryConfig memoryConfig) {
        InteractionRecordMemoryConfig config = memoryConfig.interactionRecord();
        java.util.Optional<Long> existingRecordId = storeService.findExistingRecordId(event.turn().assistantMessageId());
        if (existingRecordId.isPresent()) {
            return existingRecordId.get();
        }
        String result = completionService.complete(
                config.getPromptVersionId(),
                memoryConfig.getChatModelInstanceId(),
                Map.of("conversation_history", history));
        InteractionExtraction extraction = JSON.parseObject(result, InteractionExtraction.class);
        if (extraction == null || extraction.summary == null || extraction.summary.isBlank()) {
            throw new IllegalStateException("交互记录提取结果缺少summary");
        }
        EmbeddingModelRuntimeConfig embeddingConfig = embeddingResolver.resolve(memoryConfig.getEmbeddingModelInstanceId());
        List<float[]> vectors = embeddingGateway.embed(List.of(extraction.summary.trim()), embeddingConfig);
        if (vectors.isEmpty() || vectors.getFirst() == null || vectors.getFirst().length == 0) {
            throw new IllegalStateException("交互记录向量化未返回结果");
        }
        return storeService.save(InteractionMemoryWrite.builder()
                .userId(event.userId())
                .conversationId(event.turn().conversationId())
                .assistantMessageId(event.turn().assistantMessageId())
                .summary(extraction.summary.trim())
                .entities(MemoryData.from(extraction.entities))
                .embeddingModelInstanceId(memoryConfig.getEmbeddingModelInstanceId())
                .embedding(vectors.getFirst())
                .build());
    }

    /** 模型返回的交互记录结构。 */
    public static class InteractionExtraction {
        public String summary;
        public Map<String, Object> entities;
    }
}
