package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeIngestionJobRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeObjectDeleteRequestedEvent;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeObjectStoredEvent;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识库文档服务的一致性行为测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {
    @Mock private KnowledgeBaseRepo baseRepo;
    @Mock private KnowledgeDocumentRepo documentRepo;
    @Mock private KnowledgeIngestionJobRepo jobRepo;
    @Mock private KnowledgeObjectStorage objectStorage;
    @Mock private KnowledgeProperties properties;
    @Mock private KnowledgeConverter converter;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;

    @InjectMocks private KnowledgeDocumentServiceImpl service;

    @Test
    void shouldRegisterObjectCompensationWhenDatabaseTransactionFails() {
        when(baseRepo.findById(1L)).thenReturn(Optional.of(new KnowledgeBaseEntity()));
        when(documentRepo.existsByHash(any(), anyString())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("内容哈希重复"))
                .when(documentRepo).save(any(KnowledgeDocumentEntity.class));

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
        when(jobRepo.existsRunningByDocumentId(21L)).thenReturn(false);

        service.delete(21L, 9L);

        verify(documentRepo).delete(21L);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(
                new KnowledgeObjectDeleteRequestedEvent("knowledge/1/document.pdf"));
    }
}
