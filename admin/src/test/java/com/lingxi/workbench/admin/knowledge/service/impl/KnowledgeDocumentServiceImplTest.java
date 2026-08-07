package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkVectorRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeIndexVersionService;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeObjectDeleteRequestedEvent;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeObjectStoredEvent;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识库文档服务的一致性行为测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {
    @Mock private KnowledgeBaseRepo baseRepo;
    @Mock private KnowledgeDocumentRepo documentRepo;
    @Mock private KnowledgeChunkTaskRepo taskRepo;
    @Mock private KnowledgeChunkRepo chunkRepo;
    @Mock private KnowledgeChunkVectorRepo vectorRepo;
    @Mock private KnowledgeObjectStorage objectStorage;
    @Mock private KnowledgeProperties properties;
    @Mock private KnowledgeConverter converter;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;
    @Mock private KnowledgeIndexVersionService knowledgeIndexVersionService;

    @InjectMocks private KnowledgeDocumentServiceImpl service;

    @Test
    void shouldRegisterObjectCompensationWhenDatabaseTransactionFails() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setStatus(EnableStatus.ACTIVE);
        when(baseRepo.findById(1L)).thenReturn(Optional.of(knowledgeBase));
        when(documentRepo.existsByHash(any(), anyString())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("内容哈希重复"))
                .when(documentRepo).upsert(any(KnowledgeDocumentEntity.class));

        assertThatThrownBy(() -> service.createText(
                new CreateTextDocumentRequest(1L, "测试文档", "测试内容"), 9L))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOfSatisfying(KnowledgeObjectStoredEvent.class,
                event -> assertThat(event.objectKey()).startsWith("knowledge/1/").endsWith(".txt"));
    }

    @Test
    void shouldDeleteOriginalObjectOnlyAfterTransactionCommit() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(21L);
        document.setName("制度文档");
        document.setObjectKey("knowledge/1/document.pdf");
        when(documentRepo.findById(21L)).thenReturn(Optional.of(document));
        when(taskRepo.existsActive(21L)).thenReturn(false);

        service.delete(21L, 9L);

        InOrder deleteOrder = inOrder(vectorRepo, chunkRepo, taskRepo, documentRepo);
        deleteOrder.verify(vectorRepo).deleteByDocumentId(21L);
        deleteOrder.verify(chunkRepo).deleteByDocumentId(21L);
        deleteOrder.verify(taskRepo).deleteByDocumentId(21L);
        deleteOrder.verify(documentRepo).delete(21L);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(
                new KnowledgeObjectDeleteRequestedEvent("knowledge/1/document.pdf"));
    }

    @Test
    void shouldRejectDeletingDocumentFrozenByPublishedIndex() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(21L);
        document.setName("制度文档");
        when(documentRepo.findById(21L)).thenReturn(Optional.of(document));
        when(taskRepo.existsActive(21L)).thenReturn(false);
        when(knowledgeIndexVersionService.isPublishedDocument(21L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(21L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识索引版本冻结");
    }

    @Test
    void shouldForceUtf8ContentTypeWhenPreviewingMarkdown() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(22L);
        document.setFileType("MD");
        document.setObjectKey("knowledge/1/document.md");
        when(documentRepo.findById(22L)).thenReturn(Optional.of(document));
        when(objectStorage.presignedGetUrl(
                "knowledge/1/document.md",
                Duration.ofMinutes(15),
                "text/markdown; charset=UTF-8"))
                .thenReturn("http://localhost/preview");

        String previewUrl = service.previewUrl(22L);

        assertThat(previewUrl).isEqualTo("http://localhost/preview");
        verify(objectStorage).presignedGetUrl(
                "knowledge/1/document.md",
                Duration.ofMinutes(15),
                "text/markdown; charset=UTF-8");
    }
}
