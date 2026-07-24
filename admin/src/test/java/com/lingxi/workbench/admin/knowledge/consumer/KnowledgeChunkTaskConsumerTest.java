package com.lingxi.workbench.admin.knowledge.consumer;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.message.KnowledgeChunkTaskMessage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentParser;
import com.lingxi.workbench.admin.knowledge.service.impl.KnowledgeChunkPipelineService;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeChunkTaskConsumerTest {

    @Mock
    private KnowledgeChunkPipelineService pipelineService;

    private KnowledgeChunkTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new KnowledgeChunkTaskConsumer(pipelineService);
        ReflectionTestUtils.setField(consumer, "batchSize", 1);
    }

    @Test
    void shouldExecuteWholePipelineInOneConsumer() throws Exception {
        Long taskId = 1L;
        KnowledgeChunkPipelineService.ProcessingInput processingInput =
                KnowledgeChunkPipelineService.ProcessingInput.builder()
                        .taskId(taskId)
                        .knowledgeBaseId(2L)
                        .documentId(3L)
                        .strategy(KnowledgeChunkStrategy.PARAGRAPH)
                        .chunkSize(600)
                        .chunkOverlap(80)
                        .operatorId(4L)
                        .objectKey("knowledge/2/source.pdf")
                        .documentName("测试文档")
                        .fileName("source.pdf")
                        .fileType("PDF")
                        .sourceType(KnowledgeDocumentSourceType.FILE)
                        .build();
        KnowledgeDocumentParser.ParsedDocument parsedDocument =
                new KnowledgeDocumentParser.ParsedDocument("第一段\n\n第二段", 1);
        KnowledgeChunkPipelineService.ChunkInput chunkInput =
                KnowledgeChunkPipelineService.ChunkInput.builder()
                        .taskId(taskId)
                        .knowledgeBaseId(2L)
                        .documentId(3L)
                        .strategy(KnowledgeChunkStrategy.PARAGRAPH)
                        .chunkSize(600)
                        .chunkOverlap(80)
                        .operatorId(4L)
                        .content(parsedDocument.content())
                        .documentName("测试文档")
                        .fileName("source.pdf")
                        .fileType("PDF")
                        .sourceType(KnowledgeDocumentSourceType.FILE)
                        .pageCount(1)
                        .build();
        List<KnowledgeChunkBO> chunkResults = List.of(
                new KnowledgeChunkBO(1, "第一段", 3, null),
                new KnowledgeChunkBO(2, "第二段", 3, null));
        KnowledgeChunkEntity firstChunk = chunk(1, "第一段");
        KnowledgeChunkEntity secondChunk = chunk(2, "第二段");
        EmbeddingModelRuntimeConfig modelConfig =
                EmbeddingModelRuntimeConfig.builder()
                        .instanceId(5L)
                        .baseUrl("https://example.com")
                        .apiKey("key")
                        .modelCode("embedding-model")
                        .timeoutSeconds(30)
                        .maxRetries(1)
                        .build();
        KnowledgeChunkPipelineService.VectorInput vectorInput =
                KnowledgeChunkPipelineService.VectorInput.builder()
                        .taskId(taskId)
                        .documentId(3L)
                        .operatorId(4L)
                        .chunks(List.of(firstChunk, secondChunk))
                        .modelConfig(modelConfig)
                        .build();

        when(pipelineService.startProcessing(taskId)).thenReturn(Optional.of(processingInput));
        when(pipelineService.parse(processingInput)).thenReturn(parsedDocument);
        when(pipelineService.startChunking(processingInput, parsedDocument)).thenReturn(chunkInput);
        when(pipelineService.chunk(chunkInput)).thenReturn(chunkResults);
        when(pipelineService.startVectorize(chunkInput, chunkResults)).thenReturn(vectorInput);
        when(pipelineService.embed(List.of(firstChunk), modelConfig))
                .thenReturn(List.of(new float[]{0.1F, 0.2F}));
        when(pipelineService.embed(List.of(secondChunk), modelConfig))
                .thenReturn(List.of(new float[]{0.3F, 0.4F}));

        consumer.onMessage(new KnowledgeChunkTaskMessage(taskId));

        verify(pipelineService).updateVectorizeProgress(vectorInput, 1);
        verify(pipelineService).updateVectorizeProgress(vectorInput, 2);
        verify(pipelineService).completeProcessing(eq(vectorInput),
                argThat(vectors -> vectors.size() == 2));
        verify(pipelineService, never()).handleFailure(any(), any(), any(), any());
    }

    @Test
    void shouldIgnoreDuplicatedMessageWhenTaskCannotBeClaimed() throws Exception {
        Long taskId = 1L;
        when(pipelineService.startProcessing(taskId)).thenReturn(Optional.empty());

        consumer.onMessage(new KnowledgeChunkTaskMessage(taskId));

        verify(pipelineService, never()).parse(any());
        verify(pipelineService, never()).handleFailure(any(), eq(KnowledgeChunkTaskStatus.PENDING),
                any(), any());
    }

    private static KnowledgeChunkEntity chunk(int chunkNo, String content) {
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setChunkNo(chunkNo);
        entity.setContent(content);
        return entity;
    }
}
