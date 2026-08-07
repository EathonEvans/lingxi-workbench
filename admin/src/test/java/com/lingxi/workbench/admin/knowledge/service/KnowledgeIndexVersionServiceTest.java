package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIndexVersionEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIndexVersionTaskEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkVectorRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeIndexVersionRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识索引版本冻结、复用和一致性校验测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeIndexVersionServiceTest {
    @Mock private KnowledgeBaseRepo knowledgeBaseRepo;
    @Mock private KnowledgeDocumentRepo documentRepo;
    @Mock private KnowledgeChunkTaskRepo taskRepo;
    @Mock private KnowledgeChunkVectorRepo vectorRepo;
    @Mock private KnowledgeIndexVersionRepo indexVersionRepo;

    @InjectMocks private KnowledgeIndexVersionService service;

    @Test
    void shouldPublishImmutableVersionAndActivateIt() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase();
        prepareCandidate(knowledgeBase);
        when(knowledgeBaseRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(knowledgeBase));
        when(indexVersionRepo.findByBaseAndChecksum(any(), anyString())).thenReturn(Optional.empty());
        when(indexVersionRepo.nextVersion(1L)).thenReturn(3);
        AtomicReference<List<KnowledgeIndexVersionTaskEntity>> capturedReferences = new AtomicReference<>();
        doAnswer(invocation -> {
            KnowledgeIndexVersionEntity version = invocation.getArgument(0);
            version.setId(81L);
            capturedReferences.set(invocation.getArgument(1));
            return null;
        }).when(indexVersionRepo).insert(any(), any());

        KnowledgeIndexVersionEntity result = service.publishCurrent(1L, 7L, 9L);

        assertThat(result.getId()).isEqualTo(81L);
        assertThat(result.getVersionNo()).isEqualTo(3);
        assertThat(result.getEmbeddingDimension()).isEqualTo(1536);
        assertThat(result.getChunkCount()).isEqualTo(5);
        assertThat(result.getContentChecksum()).hasSize(64);
        assertThat(knowledgeBase.getPublishedIndexVersionId()).isEqualTo(81L);
        assertThat(knowledgeBase.getUpdatedBy()).isEqualTo(9L);
        verify(knowledgeBaseRepo).save(knowledgeBase);

        verify(indexVersionRepo).insert(any(KnowledgeIndexVersionEntity.class), any());
        assertThat(capturedReferences.get()).hasSize(2);
    }

    @Test
    void shouldReuseVersionWithSameFrozenTaskSet() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase();
        prepareCandidate(knowledgeBase);
        KnowledgeIndexVersionEntity existing = new KnowledgeIndexVersionEntity();
        existing.setId(88L);
        when(knowledgeBaseRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(knowledgeBase));
        when(indexVersionRepo.findByBaseAndChecksum(any(), anyString())).thenReturn(Optional.of(existing));

        KnowledgeIndexVersionEntity result = service.publishCurrent(1L, 7L, 9L);

        assertThat(result).isSameAs(existing);
        assertThat(knowledgeBase.getPublishedIndexVersionId()).isEqualTo(88L);
        verify(indexVersionRepo, never()).insert(any(), any());
        verify(knowledgeBaseRepo).save(knowledgeBase);
    }

    @Test
    void shouldExposeMissingOrInconsistentVectorsAsBusinessValidationFailure() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase();
        prepareDocumentsAndTasks(knowledgeBase);
        when(knowledgeBaseRepo.findById(1L)).thenReturn(Optional.of(knowledgeBase));
        when(vectorRepo.findConsistentDimension(List.of(11L, 12L), 5))
                .thenThrow(new IllegalStateException("dimension mismatch"));

        assertThatThrownBy(() -> service.validatePublishable(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识索引向量缺失或维度不一致");
    }

    private void prepareCandidate(KnowledgeBaseEntity knowledgeBase) {
        prepareDocumentsAndTasks(knowledgeBase);
        when(vectorRepo.findConsistentDimension(List.of(11L, 12L), 5)).thenReturn(1536);
    }

    private void prepareDocumentsAndTasks(KnowledgeBaseEntity knowledgeBase) {
        KnowledgeDocumentEntity first = document(21L, 11L);
        KnowledgeDocumentEntity second = document(22L, 12L);
        when(documentRepo.findIndexableByBaseId(knowledgeBase.getId())).thenReturn(List.of(first, second));
        when(taskRepo.findByIds(List.of(11L, 12L))).thenReturn(List.of(task(11L, 2), task(12L, 3)));
    }

    private static KnowledgeBaseEntity knowledgeBase() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setEmbeddingModelInstanceId(7L);
        entity.setStatus(EnableStatus.ACTIVE);
        return entity;
    }

    private static KnowledgeDocumentEntity document(Long id, Long taskId) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(id);
        entity.setActiveChunkTaskId(taskId);
        return entity;
    }

    private static KnowledgeChunkTaskEntity task(Long id, int chunkCount) {
        KnowledgeChunkTaskEntity entity = new KnowledgeChunkTaskEntity();
        entity.setId(id);
        entity.setKnowledgeBaseId(1L);
        entity.setEmbeddingModelInstanceId(7L);
        entity.setVersionNo(1);
        entity.setStatus(KnowledgeChunkTaskStatus.SUCCEEDED);
        entity.setChunkCount(chunkCount);
        entity.setVectorizedCount(chunkCount);
        entity.setTotalTokens((long) chunkCount * 100);
        entity.setUpdatedAt(OffsetDateTime.parse("2026-08-07T10:00:00+08:00"));
        return entity;
    }
}
