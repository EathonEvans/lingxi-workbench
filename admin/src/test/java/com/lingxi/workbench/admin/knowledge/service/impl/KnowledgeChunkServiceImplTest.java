package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentChunker;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文档手动分块业务测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeChunkServiceImplTest {
    @Mock private KnowledgeDocumentRepo documentRepo;
    @Mock private KnowledgeDocumentChunker documentChunker;
    @Mock private KnowledgeConverter converter;
    @Mock private AuditService auditService;

    @InjectMocks private KnowledgeChunkServiceImpl service;

    @Test
    void shouldExecuteChunkOnlyAfterDocumentParsed() {
        KnowledgeDocumentEntity document = parsedDocument();
        List<KnowledgeChunkBO> chunks = List.of(
                new KnowledgeChunkBO(1, "第一段", 10, null),
                new KnowledgeChunkBO(2, "第二段", 12, "安装"));
        when(documentRepo.findById(11L)).thenReturn(Optional.of(document));
        when(documentChunker.chunk(
                        document.getParsedContent(), KnowledgeChunkStrategy.PARAGRAPH, 600, 80))
                .thenReturn(chunks);
        when(converter.toChunkVOList(chunks))
                .thenReturn(List.of(
                        new KnowledgeChunkVO(1, "第一段", 10, null),
                        new KnowledgeChunkVO(2, "第二段", 12, "安装")));

        KnowledgeChunkResultVO result = service.execute(
                11L,
                new ExecuteKnowledgeChunkRequest(KnowledgeChunkStrategy.PARAGRAPH, 600, 80),
                9L);

        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.totalTokens()).isEqualTo(22);
        assertThat(result.chunks()).extracting("content").containsExactly("第一段", "第二段");
        verify(auditService).record(
                "KNOWLEDGE", "DOCUMENT", 11L, 9L,
                "DOCUMENT_CHUNK_EXECUTE", "策略=PARAGRAPH，Chunk数量=2");
    }

    @Test
    void shouldRejectDocumentBeforeParsingCompleted() {
        KnowledgeDocumentEntity document = parsedDocument();
        document.setStatus(KnowledgeDocumentStatus.PARSING);
        when(documentRepo.findById(11L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.execute(
                        11L,
                        new ExecuteKnowledgeChunkRequest(KnowledgeChunkStrategy.PARAGRAPH, 600, 80),
                        9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("解析完成后");
    }

    @Test
    void shouldRejectOverlapNotLessThanChunkSize() {
        when(documentRepo.findById(11L)).thenReturn(Optional.of(parsedDocument()));

        assertThatThrownBy(() -> service.execute(
                        11L,
                        new ExecuteKnowledgeChunkRequest(KnowledgeChunkStrategy.FIXED, 100, 100),
                        9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须小于");
    }

    private static KnowledgeDocumentEntity parsedDocument() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(11L);
        document.setName("产品手册");
        document.setStatus(KnowledgeDocumentStatus.PARSED);
        document.setParsedContent("第一段\n\n第二段");
        return document;
    }
}
