package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.request.UpdateKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkVectorRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentChunker;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeChunkRequestedEvent;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文档异步分块任务业务测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeChunkServiceImplTest {
    @Mock private KnowledgeDocumentRepo documentRepo;
    @Mock private KnowledgeBaseRepo baseRepo;
    @Mock private KnowledgeChunkTaskRepo taskRepo;
    @Mock private KnowledgeChunkRepo chunkRepo;
    @Mock private KnowledgeChunkVectorRepo vectorRepo;
    @Mock private KnowledgeEmbeddingModelResolver embeddingModelResolver;
    @Mock private KnowledgeDocumentChunker documentChunker;
    @Mock private EmbeddingGateway embeddingGateway;
    @Mock private KnowledgeConverter converter;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private KnowledgeChunkServiceImpl service;

    @Test
    void shouldCreatePendingTaskAfterUserConfirmation() {
        KnowledgeDocumentEntity document = pendingDocument();
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        knowledgeBase.setEmbeddingModelInstanceId(7L);
        knowledgeBase.setStatus(EnableStatus.ACTIVE);
        when(documentRepo.findById(11L)).thenReturn(Optional.of(document));
        when(baseRepo.findById(1L)).thenReturn(Optional.of(knowledgeBase));
        when(documentRepo.claimProcessing(11L, KnowledgeChunkStrategy.PARAGRAPH, 600, 80, 9L))
                .thenReturn(true);
        when(taskRepo.nextVersion(11L)).thenReturn(2);
        doAnswer(invocation -> {
            KnowledgeChunkTaskEntity task = invocation.getArgument(0);
            task.setId(19L);
            return null;
        }).when(taskRepo).upsert(any(KnowledgeChunkTaskEntity.class));

        service.execute(11L,
                new ExecuteKnowledgeChunkRequest(KnowledgeChunkStrategy.PARAGRAPH, 600, 80), 9L);

        ArgumentCaptor<KnowledgeChunkTaskEntity> taskCaptor =
                ArgumentCaptor.forClass(KnowledgeChunkTaskEntity.class);
        verify(taskRepo).upsert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(KnowledgeChunkTaskStatus.PENDING);
        assertThat(taskCaptor.getValue().getEmbeddingModelInstanceId()).isEqualTo(7L);
        verify(eventPublisher).publishEvent(new KnowledgeChunkRequestedEvent(19L));
    }

    @Test
    void shouldRejectDocumentAlreadyRunning() {
        KnowledgeDocumentEntity document = pendingDocument();
        document.setStatus(KnowledgeDocumentStatus.RUNNING);
        when(documentRepo.findById(11L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.execute(11L,
                new ExecuteKnowledgeChunkRequest(KnowledgeChunkStrategy.PARAGRAPH, 600, 80), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在处理中");
    }

    @Test
    void shouldRejectOverlapNotLessThanChunkSize() {
        when(documentRepo.findById(11L)).thenReturn(Optional.of(pendingDocument()));

        assertThatThrownBy(() -> service.execute(11L,
                new ExecuteKnowledgeChunkRequest(KnowledgeChunkStrategy.FIXED, 100, 100), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须小于");
    }

    @Test
    void shouldUpdateChunkAndRefreshVectorTogether() {
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(31L);
        chunk.setTaskId(19L);
        chunk.setDocumentId(11L);
        chunk.setChunkNo(2);
        chunk.setContent("旧正文");
        chunk.setContentHash("old-hash");
        chunk.setTokenCount(3);
        KnowledgeChunkTaskEntity task = new KnowledgeChunkTaskEntity();
        task.setId(19L);
        task.setEmbeddingModelInstanceId(7L);
        task.setStatus(KnowledgeChunkTaskStatus.SUCCEEDED);
        task.setTotalTokens(20L);
        KnowledgeDocumentEntity document = pendingDocument();
        document.setActiveChunkTaskId(19L);
        EmbeddingModelRuntimeConfig modelConfig =
                EmbeddingModelRuntimeConfig.builder()
                        .instanceId(7L)
                        .baseUrl("http://localhost")
                        .apiKey("secret")
                        .modelCode("embedding")
                        .timeoutSeconds(30)
                        .maxRetries(1)
                        .build();
        float[] vector = new float[]{0.1F, 0.2F};
        KnowledgeChunkVO expected = KnowledgeChunkVO.builder()
                .id("31").chunkNo(2).content("新正文").charCount(3).tokenCount(4).metadata(null).build();

        when(chunkRepo.findById(31L)).thenReturn(Optional.of(chunk));
        when(taskRepo.findById(19L)).thenReturn(Optional.of(task));
        when(documentRepo.findById(11L)).thenReturn(Optional.of(document));
        when(embeddingModelResolver.resolve(7L)).thenReturn(modelConfig);
        when(documentChunker.estimateTokens("新正文")).thenReturn(4);
        when(embeddingGateway.embed(List.of("新正文"), modelConfig)).thenReturn(List.of(vector));
        when(converter.toChunkVO(chunk)).thenReturn(expected);
        doAnswer(invocation -> invocation
                .<org.springframework.transaction.support.TransactionCallback<KnowledgeChunkVO>>getArgument(0)
                .doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class)))
                .when(transactionTemplate)
                .execute(any());

        KnowledgeChunkVO result = service.update(
                31L, new UpdateKnowledgeChunkRequest("新正文"), 9L);

        assertThat(result).isEqualTo(expected);
        assertThat(chunk.getContent()).isEqualTo("新正文");
        assertThat(task.getTotalTokens()).isEqualTo(21L);
        verify(vectorRepo).upsert(chunk, vector);
        verify(auditService).record(
                "KNOWLEDGE", "CHUNK", 31L, 9L,
                "KNOWLEDGE_CHUNK_UPDATE", "文档=产品手册，Chunk=2");
    }

    private static KnowledgeDocumentEntity pendingDocument() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(11L);
        document.setKnowledgeBaseId(1L);
        document.setName("产品手册");
        document.setStatus(KnowledgeDocumentStatus.PENDING);
        return document;
    }
}
