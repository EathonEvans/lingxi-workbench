package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkTaskStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.model.bo.DocChunkBO;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkMetadata;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkVectorRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentChunker;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentParser;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * RocketMQ消费者调用的解析、分块、向量化与索引流水线。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeChunkPipelineService {
    private static final int MAX_RETRY_COUNT = 3;

    private final KnowledgeChunkTaskRepo taskRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeChunkRepo chunkRepo;
    private final KnowledgeChunkVectorRepo vectorRepo;
    private final KnowledgeObjectStorage objectStorage;
    private final KnowledgeDocumentParser documentParser;
    private final KnowledgeDocumentChunker documentChunker;
    private final KnowledgeEmbeddingModelResolver modelResolver;
    private final EmbeddingGateway embeddingGateway;
    private final AuditService auditService;

    /**
     * 原子抢占等待处理的任务，并返回解析、分块所需的不可变输入。
     */
    @Transactional
    public Optional<ProcessingInput> startProcessing(Long taskId) {
        if (!taskRepo.claimParsing(taskId, OffsetDateTime.now())) {
            return Optional.empty();
        }
        KnowledgeChunkTaskEntity task = requireTask(taskId);
        KnowledgeDocumentEntity document = requireDocument(task.getDocumentId());
        return Optional.of(ProcessingInput.builder()
                .taskId(task.getId())
                .knowledgeBaseId(task.getKnowledgeBaseId())
                .documentId(task.getDocumentId())
                .strategy(task.getStrategy())
                .chunkSize(task.getChunkSize())
                .chunkOverlap(task.getChunkOverlap())
                .operatorId(task.getCreatedBy())
                .objectKey(document.getObjectKey())
                .documentName(document.getName())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .sourceType(document.getSourceType())
                .build());
    }

    /**
     * 从对象存储读取原始文件并提取标准化正文，长耗时解析不持有数据库事务。
     */
    public KnowledgeDocumentParser.ParsedDocument parse(ProcessingInput input) throws Exception {
        try (InputStream stream = objectStorage.get(input.getObjectKey())) {
            return documentParser.parse(stream, input.getFileName());
        }
    }

    /**
     * 解析完成后推进到分块阶段，并保存可直接展示的页数信息。
     */
    @Transactional
    public ChunkInput startChunking(ProcessingInput input, KnowledgeDocumentParser.ParsedDocument parsedDocument) {
        if (!taskRepo.markChunking(input.getTaskId())) {
            throw new IllegalStateException("文档任务状态已变化，无法开始分块：" + input.getTaskId());
        }
        KnowledgeDocumentEntity document = requireDocument(input.getDocumentId());
        document.setPageCount(parsedDocument.pageCount());
        documentRepo.upsert(document);
        return ChunkInput.builder()
                .taskId(input.getTaskId())
                .knowledgeBaseId(input.getKnowledgeBaseId())
                .documentId(input.getDocumentId())
                .strategy(input.getStrategy())
                .chunkSize(input.getChunkSize())
                .chunkOverlap(input.getChunkOverlap())
                .operatorId(input.getOperatorId())
                .content(parsedDocument.content())
                .documentName(input.getDocumentName())
                .fileName(input.getFileName())
                .fileType(input.getFileType())
                .sourceType(input.getSourceType())
                .pageCount(parsedDocument.pageCount())
                .build();
    }

    /**
     * 纯CPU分块在事务外执行，避免长时间占用数据库连接。
     */
    public List<DocChunkBO> chunk(ChunkInput input) {
        return documentChunker.chunk(input.getContent(), input.getStrategy(), input.getChunkSize(), input.getChunkOverlap());
    }

    /**
     * 将分块结果转换为待持久化实体，并把任务推进到向量化阶段。
     *
     * <p>此处只更新任务进度，不提前写入Chunk。Embedding调用成功后，
     * Chunk、向量、任务和文档会在同一个数据库事务中统一提交，避免暴露半成品。</p>
     */
    @Transactional
    public VectorInput startVectorize(ChunkInput input, List<DocChunkBO> chunks) {
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文档解析后没有生成有效Chunk");
        }
        if (!taskRepo.markVectorizing(input.getTaskId())) {
            throw new IllegalStateException("文档任务状态已变化，无法开始向量化：" + input.getTaskId());
        }
        List<KnowledgeChunkEntity> entities = new ArrayList<>(chunks.size());
        long totalTokens = 0L;
        int previousStart = -1;
        for (DocChunkBO chunk : chunks) {
            // 计算截取开始位置
            int charStart = input.getContent().indexOf(chunk.content(), previousStart + 1);

            KnowledgeChunkEntity entity = build(input, chunk, charStart);

            entities.add(entity);
            totalTokens += chunk.tokenCount();
            if (charStart >= 0) {
                previousStart = charStart;
            }
        }
        KnowledgeChunkTaskEntity task = requireTask(input.getTaskId());
        task.setProgress(60);
        task.setChunkCount(entities.size());
        task.setVectorizedCount(0);
        task.setTotalTokens(totalTokens);
        taskRepo.upsert(task);
        EmbeddingModelRuntimeConfig modelConfig = modelResolver.resolve(task.getEmbeddingModelInstanceId());
        return VectorInput.builder()
                .taskId(task.getId())
                .documentId(task.getDocumentId())
                .operatorId(task.getCreatedBy())
                .chunks(List.copyOf(entities))
                .modelConfig(modelConfig)
                .build();
    }

    private KnowledgeChunkEntity build(ChunkInput input, DocChunkBO chunk, int charStart) {

        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setTaskId(input.getTaskId());
        entity.setKnowledgeBaseId(input.getKnowledgeBaseId());
        entity.setDocumentId(input.getDocumentId());
        entity.setChunkNo(chunk.chunkNo());
        entity.setContent(chunk.content());
        entity.setContentHash(sha256(chunk.content()));
        entity.setCharCount(chunk.content().length());
        entity.setTokenCount(chunk.tokenCount());
        Integer startOffset = charStart >= 0 ? charStart : null;
        Integer endOffset = charStart >= 0 ? charStart + chunk.content().length() : null;
        entity.setMetadata(KnowledgeChunkMetadata.builder()
                .documentName(input.getDocumentName())
                .fileName(input.getFileName())
                .fileType(input.getFileType())
                .sourceType(input.getSourceType())
                .strategy(input.getStrategy())
                .sectionTitle(chunk.sectionTitle())
                .pageNumber(null)
                .pageCount(input.getPageCount())
                .charStart(startOffset)
                .charEnd(endOffset)
                .build());
        entity.setEnabled(true);
        entity.setCreatedBy(input.getOperatorId());
        entity.setUpdatedBy(input.getOperatorId());

        return entity;
    }


    /**
     * 调用Embedding模型，调用过程不持有数据库事务。
     */
    public List<float[]> embed(List<KnowledgeChunkEntity> chunks, EmbeddingModelRuntimeConfig config) {
        return embeddingGateway.embed(chunks.stream().map(KnowledgeChunkEntity::getContent).toList(), config);
    }

    /**
     * 更新Embedding批处理进度，供管理端轮询展示。
     */
    @Transactional
    public void updateVectorizeProgress(VectorInput input, int processedCount) {
        KnowledgeChunkTaskEntity task = requireTask(input.getTaskId());
        if (task.getStatus() != KnowledgeChunkTaskStatus.VECTORIZING) {
            throw new IllegalStateException("文档任务不处于向量化状态：" + input.getTaskId());
        }
        task.setVectorizedCount(processedCount);
        task.setProgress(60 + (int) Math.floor(processedCount * 35D / input.getChunks().size()));
        taskRepo.upsert(task);
    }

    /**
     * 在单个数据库事务中写入Chunk和向量，并切换文档生效版本。
     *
     * <p>对象存储读取、Tika解析和Embedding远程调用不属于数据库事务，
     * 只有全部外部计算成功后才进入本方法提交最终数据。</p>
     */
    @Transactional
    public void completeProcessing(VectorInput input, List<float[]> vectors) {
        if (input.getChunks().size() != vectors.size()) {
            throw new IllegalArgumentException("Chunk数量与向量数量不一致");
        }
        KnowledgeChunkTaskEntity task = requireTask(input.getTaskId());
        if (task.getStatus() != KnowledgeChunkTaskStatus.VECTORIZING) {
            throw new IllegalStateException("文档任务不处于向量化状态：" + input.getTaskId());
        }
        KnowledgeDocumentEntity document = requireDocument(input.getDocumentId());
        vectorRepo.deleteByTaskId(input.getTaskId());
        chunkRepo.replaceTaskChunks(input.getTaskId(), input.getChunks());
        List<KnowledgeChunkEntity> persistedChunks = chunkRepo.findByTaskId(input.getTaskId());
        if (persistedChunks.size() != vectors.size()) {
            throw new IllegalStateException("持久化后的Chunk数量与向量数量不一致");
        }
        vectorRepo.upsertBatch(persistedChunks, vectors);
        task.setStatus(KnowledgeChunkTaskStatus.SUCCEEDED);
        task.setProgress(100);
        task.setVectorizedCount(task.getChunkCount());
        task.setFinishedAt(OffsetDateTime.now());
        taskRepo.upsert(task);
        document.setActiveChunkTaskId(task.getId());
        document.setChunkCount(task.getChunkCount());
        document.setStatus(KnowledgeDocumentStatus.SUCCESS);
        document.setErrorCode(null);
        document.setErrorMessage(null);
        documentRepo.upsert(document);
        auditService.record("KNOWLEDGE", "DOCUMENT", document.getId(), input.getOperatorId(),
                "DOCUMENT_PROCESS_COMPLETE", "任务=" + task.getId() + "，Chunk数量=" + task.getChunkCount());
    }

    /**
     * 记录阶段失败。达到上限前恢复为等待状态并交给RocketMQ重试，
     * 达到上限后才终止任务并更新文档状态。
     *
     * @return true表示仍可重试，false表示已经终止
     */
    @Transactional
    public boolean handleFailure(Long taskId, KnowledgeChunkTaskStatus retryStatus,
                                  String errorCode, Exception exception) {
        KnowledgeChunkTaskEntity task = requireTask(taskId);
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        String errorMessage = safeMessage(exception);
        if (retryCount < MAX_RETRY_COUNT) {
            task.setRetryCount(retryCount + 1);
            task.setStatus(retryStatus);
            task.setProgress(0);
            task.setVectorizedCount(0);
            task.setErrorCode(errorCode);
            task.setErrorMessage(errorMessage);
            taskRepo.upsert(task);
            return true;
        }
        task.setStatus(KnowledgeChunkTaskStatus.FAILED);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(OffsetDateTime.now());
        taskRepo.upsert(task);
        KnowledgeDocumentEntity document = requireDocument(task.getDocumentId());
        document.setStatus(KnowledgeDocumentStatus.FAILED);
        document.setErrorCode(errorCode);
        document.setErrorMessage(errorMessage);
        documentRepo.upsert(document);
        return false;
    }

    private KnowledgeChunkTaskEntity requireTask(Long id) {
        return taskRepo.findById(id).orElseThrow(() ->
                new IllegalStateException("分块任务不存在：" + id));
    }

    private KnowledgeDocumentEntity requireDocument(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> new IllegalStateException("知识库文档不存在：" + id));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256算法", exception);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "任务执行失败";
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    /**
     * 解析阶段输入参数。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProcessingInput {
        /** 分块任务ID */
        private Long taskId;
        /** 知识库ID */
        private Long knowledgeBaseId;
        /** 文档ID */
        private Long documentId;
        /** 分块策略 */
        private KnowledgeChunkStrategy strategy;
        /** Chunk目标Token数 */
        private int chunkSize;
        /** Chunk重叠Token数 */
        private int chunkOverlap;
        /** 操作人ID */
        private Long operatorId;
        /** 对象存储Key */
        private String objectKey;
        /** 文档名称 */
        private String documentName;
        /** 原始文件名 */
        private String fileName;
        /** 文件类型 */
        private String fileType;
        /** 文档来源类型 */
        private KnowledgeDocumentSourceType sourceType;
    }

    /**
     * 分块阶段输入参数。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ChunkInput {
        /** 分块任务ID */
        private Long taskId;
        /** 知识库ID */
        private Long knowledgeBaseId;
        /** 文档ID */
        private Long documentId;
        /** 分块策略 */
        private KnowledgeChunkStrategy strategy;
        /** Chunk目标Token数 */
        private int chunkSize;
        /** Chunk重叠Token数 */
        private int chunkOverlap;
        /** 操作人ID */
        private Long operatorId;
        /** 解析标准化正文 */
        private String content;
        /** 文档名称 */
        private String documentName;
        /** 原始文件名 */
        private String fileName;
        /** 文件类型 */
        private String fileType;
        /** 文档来源类型 */
        private KnowledgeDocumentSourceType sourceType;
        /** 文档总页数 */
        private Integer pageCount;
    }

    /**
     * 向量化阶段输入参数。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class VectorInput {
        /** 分块任务ID */
        private Long taskId;
        /** 文档ID */
        private Long documentId;
        /** 操作人ID */
        private Long operatorId;
        /** 待向量化的Chunk实体列表 */
        private List<KnowledgeChunkEntity> chunks;
        /** Embedding模型运行时配置 */
        private EmbeddingModelRuntimeConfig modelConfig;
    }
}
