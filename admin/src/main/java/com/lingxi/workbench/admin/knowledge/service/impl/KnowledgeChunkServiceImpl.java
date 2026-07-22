package com.lingxi.workbench.admin.knowledge.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeChunkService;
import com.lingxi.workbench.admin.knowledge.service.startegy.chunk.AbstractDocChunker;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 知识库文档手动分块业务实现。 */
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {
    private final KnowledgeDocumentRepo documentRepo;
    private final AbstractDocChunker documentChunker;
    private final KnowledgeConverter converter;
    private final AuditService auditService;

    @Override
    public KnowledgeChunkResultVO execute(
            Long documentId, ExecuteKnowledgeChunkRequest request, Long operatorId) {
        KnowledgeDocumentEntity document = documentRepo.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库文档不存在"));
        if (document.getStatus() != KnowledgeDocumentStatus.PARSED
                || CharSequenceUtil.isBlank(document.getParsedContent())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档解析完成后才能执行分块");
        }
        if (request.chunkOverlap() >= request.chunkSize()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Chunk重叠大小必须小于Chunk大小");
        }

        // 分块是用户确认后的独立步骤，不再由文档解析链路自动触发。
        List<KnowledgeChunkBO> chunks = documentChunker.chunk(
                document.getParsedContent(),
                request.strategy(),
                request.chunkSize(),
                request.chunkOverlap());
        List<KnowledgeChunkVO> chunkViews = converter.toChunkVOList(chunks);
        long totalTokens = chunks.stream().mapToLong(KnowledgeChunkBO::tokenCount).sum();

        // TODO Chunk落库接入后，在此处短事务执行幂等替换并流转文档状态。
        auditService.record("KNOWLEDGE", "DOCUMENT", documentId, operatorId, "DOCUMENT_CHUNK_EXECUTE", "策略=" + request.strategy().getCode() + "，Chunk数量=" + chunks.size());
        return new KnowledgeChunkResultVO(
                String.valueOf(document.getId()),
                document.getName(),
                request.strategy(),
                request.chunkSize(),
                request.chunkOverlap(),
                chunks.size(),
                totalTokens,
                chunkViews);
    }
}
