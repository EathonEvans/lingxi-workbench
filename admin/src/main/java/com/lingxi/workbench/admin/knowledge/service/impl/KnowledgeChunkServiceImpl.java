package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkMetadata;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeChunkPageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.UpdateKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkVectorRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeChunkService;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentChunker;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeChunkRequestedEvent;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 知识库文档异步处理任务业务实现。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeBaseRepo baseRepo;
    private final KnowledgeChunkTaskRepo taskRepo;
    private final KnowledgeChunkRepo chunkRepo;
    private final KnowledgeChunkVectorRepo vectorRepo;
    private final KnowledgeEmbeddingModelResolver embeddingModelResolver;
    private final KnowledgeDocumentChunker documentChunker;
    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeConverter converter;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public KnowledgeChunkResultVO execute(Long documentId, ExecuteKnowledgeChunkRequest request, Long operatorId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        if (document.getStatus() == KnowledgeDocumentStatus.RUNNING) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档正在处理中，请勿重复提交");
        }
        if (request.chunkOverlap() >= request.chunkSize()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Chunk重叠大小必须小于Chunk大小");
        }
        if (taskRepo.existsActive(documentId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该文档已有正在执行的分块任务");
        }
        KnowledgeBaseEntity knowledgeBase = baseRepo.findById(document.getKnowledgeBaseId()).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
        if (knowledgeBase.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库已停用，不能执行文档处理");
        }
        if (knowledgeBase.getEmbeddingModelInstanceId() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "请先为知识库配置Embedding模型");
        }
        embeddingModelResolver.validate(knowledgeBase.getEmbeddingModelInstanceId());
        if (!documentRepo.claimProcessing(documentId, request.strategy(), request.chunkSize(), request.chunkOverlap(), operatorId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档正在处理中，请勿重复提交");
        }

        KnowledgeChunkTaskEntity task = new KnowledgeChunkTaskEntity();
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(documentId);
        task.setEmbeddingModelInstanceId(knowledgeBase.getEmbeddingModelInstanceId());
        task.setVersionNo(taskRepo.nextVersion(documentId));
        task.setStrategy(request.strategy());
        task.setChunkSize(request.chunkSize());
        task.setChunkOverlap(request.chunkOverlap());
        task.setStatus(KnowledgeChunkTaskStatus.PENDING);
        task.setProgress(0);
        task.setChunkCount(0);
        task.setVectorizedCount(0);
        task.setTotalTokens(0L);
        task.setRetryCount(0);
        task.setCreatedBy(operatorId);
        task.setUpdatedBy(operatorId);
        taskRepo.upsert(task);

        auditService.record("KNOWLEDGE", "DOCUMENT", documentId, operatorId, "DOCUMENT_PROCESS_SUBMIT", "任务=" + task.getId() + "，策略=" + request.strategy().getCode());
        eventPublisher.publishEvent(new KnowledgeChunkRequestedEvent(task.getId()));
        return converter.toChunkTaskVO(task, document.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeChunkResultVO task(Long taskId) {
        KnowledgeChunkTaskEntity task = requireTask(taskId);
        return converter.toChunkTaskVO(task, requireDocument(task.getDocumentId()).getName());
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeChunkResultVO latestTask(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        return taskRepo.findLatestByDocumentId(documentId).map(task -> converter.toChunkTaskVO(task, document.getName())).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeChunkVO> page(KnowledgeChunkPageQuery query) {
        requireTask(query.getTaskId());
        return chunkRepo.findPage(query).map(converter::toChunkVO);
    }

    @Override
    public KnowledgeChunkVO update(Long chunkId, UpdateKnowledgeChunkRequest request, Long operatorId) {
        KnowledgeChunkEntity snapshot = requireChunk(chunkId);
        KnowledgeChunkTaskEntity task = requireTask(snapshot.getTaskId());
        KnowledgeDocumentEntity document = requireDocument(snapshot.getDocumentId());
        if (task.getStatus() != KnowledgeChunkTaskStatus.SUCCEEDED
                || !Objects.equals(document.getActiveChunkTaskId(), task.getId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "只能编辑当前生效版本的Chunk");
        }

        String content = request.content().trim();
        int tokenCount = documentChunker.estimateTokens(content);
        EmbeddingModelRuntimeConfig modelConfig = embeddingModelResolver.resolve(task.getEmbeddingModelInstanceId());
        List<float[]> vectors = embeddingGateway.embed(List.of(content), modelConfig);
        if (vectors.size() != 1) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "Chunk向量生成结果异常");
        }

        return transactionTemplate.execute(status -> {
            KnowledgeChunkEntity entity = requireChunk(chunkId);
            if (!Objects.equals(entity.getContentHash(), snapshot.getContentHash())) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "Chunk已被其他操作修改，请刷新后重试");
            }
            int oldTokenCount = entity.getTokenCount();
            entity.setContent(content);
            entity.setContentHash(sha256(content));
            entity.setCharCount(content.length());
            entity.setTokenCount(tokenCount);
            entity.setUpdatedBy(operatorId);
            entity.setMetadata(resetSourceOffset(entity.getMetadata()));
            chunkRepo.upsert(entity);
            vectorRepo.upsert(entity, vectors.getFirst());

            KnowledgeChunkTaskEntity currentTask = requireTask(entity.getTaskId());
            currentTask.setTotalTokens(Math.max(0L, currentTask.getTotalTokens() - oldTokenCount + tokenCount));
            currentTask.setUpdatedBy(operatorId);
            taskRepo.upsert(currentTask);
            auditService.record("KNOWLEDGE", "CHUNK", chunkId, operatorId, "KNOWLEDGE_CHUNK_UPDATE", "文档=" + document.getName() + "，Chunk=" + entity.getChunkNo());
            return converter.toChunkVO(entity);
        });
    }

    private KnowledgeChunkTaskEntity requireTask(Long id) {
        return taskRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "分块任务不存在"));
    }

    private KnowledgeDocumentEntity requireDocument(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库文档不存在"));
    }

    private KnowledgeChunkEntity requireChunk(Long id) {
        return chunkRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Chunk不存在"));
    }

    /**
     * 人工修改后正文不再对应原文字符区间，清空偏移量避免产生错误溯源信息。
     */
    private static KnowledgeChunkMetadata resetSourceOffset(KnowledgeChunkMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return new KnowledgeChunkMetadata(metadata.documentName(), metadata.fileName(), metadata.fileType(), metadata.sourceType(), metadata.strategy(), metadata.sectionTitle(), metadata.pageNumber(), metadata.pageCount(), null, null);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256算法", exception);
        }
    }
}
