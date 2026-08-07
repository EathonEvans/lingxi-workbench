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
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 从知识库当前生效文档构建并发布不可变索引版本。 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexVersionService {
    private static final String DISTANCE_TYPE = "COSINE";

    private final KnowledgeBaseRepo knowledgeBaseRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeChunkTaskRepo taskRepo;
    private final KnowledgeChunkVectorRepo vectorRepo;
    private final KnowledgeIndexVersionRepo indexVersionRepo;

    /** 校验当前知识库是否具备可冻结的一致索引，不产生数据库写入。 */
    @Transactional(readOnly = true)
    public void validatePublishable(Long knowledgeBaseId, Long embeddingModelInstanceId) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        inspect(knowledgeBase, embeddingModelInstanceId);
    }

    /** 冻结当前生效任务；相同内容复用既有版本，并原子切换知识库生效指针。 */
    @Transactional
    public KnowledgeIndexVersionEntity publishCurrent(Long knowledgeBaseId, Long embeddingModelInstanceId, Long operatorId) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepo.findByIdForUpdate(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
        IndexCandidate candidate = inspect(knowledgeBase, embeddingModelInstanceId);
        KnowledgeIndexVersionEntity version = indexVersionRepo
                .findByBaseAndChecksum(knowledgeBaseId, candidate.checksum())
                .orElseGet(() -> insertVersion(knowledgeBase, candidate, operatorId));
        if (!Objects.equals(knowledgeBase.getPublishedIndexVersionId(), version.getId())) {
            knowledgeBase.setPublishedIndexVersionId(version.getId());
            knowledgeBase.setUpdatedBy(operatorId);
            knowledgeBaseRepo.save(knowledgeBase);
        }
        return version;
    }

    public boolean isPublishedTask(Long taskId) {
        return indexVersionRepo.existsByTaskId(taskId);
    }

    public boolean isPublishedDocument(Long documentId) {
        return indexVersionRepo.existsByDocumentId(documentId);
    }

    public boolean hasPublishedVersion(Long knowledgeBaseId) {
        return indexVersionRepo.existsByKnowledgeBaseId(knowledgeBaseId);
    }

    private IndexCandidate inspect(KnowledgeBaseEntity knowledgeBase, Long embeddingModelInstanceId) {
        if (knowledgeBase.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库已停用，不能发布知识搜索能力");
        }
        if (!Objects.equals(knowledgeBase.getEmbeddingModelInstanceId(), embeddingModelInstanceId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库Embedding模型与Agent绑定不一致");
        }
        List<KnowledgeDocumentEntity> documents = documentRepo.findIndexableByBaseId(knowledgeBase.getId());
        if (documents.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库没有可发布的已向量化文档");
        }
        List<Long> taskIds = documents.stream().map(KnowledgeDocumentEntity::getActiveChunkTaskId).toList();
        Map<Long, KnowledgeChunkTaskEntity> tasks = taskRepo.findByIds(taskIds).stream()
                .collect(Collectors.toMap(KnowledgeChunkTaskEntity::getId, Function.identity()));
        if (tasks.size() != taskIds.size()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库生效分块任务不存在");
        }
        int chunkCount = 0;
        for (Long taskId : taskIds) {
            KnowledgeChunkTaskEntity task = tasks.get(taskId);
            validateTask(knowledgeBase, embeddingModelInstanceId, task);
            chunkCount += task.getChunkCount();
        }
        int dimension;
        try {
            dimension = vectorRepo.findConsistentDimension(taskIds, chunkCount);
        } catch (IllegalStateException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识索引向量缺失或维度不一致");
        }
        String checksum = checksum(tasks.values().stream().sorted(Comparator.comparing(KnowledgeChunkTaskEntity::getId)).toList());
        return new IndexCandidate(documents, dimension, chunkCount, checksum);
    }

    private KnowledgeIndexVersionEntity insertVersion(KnowledgeBaseEntity knowledgeBase, IndexCandidate candidate, Long operatorId) {
        KnowledgeIndexVersionEntity version = new KnowledgeIndexVersionEntity();
        version.setKnowledgeBaseId(knowledgeBase.getId());
        version.setVersionNo(indexVersionRepo.nextVersion(knowledgeBase.getId()));
        version.setEmbeddingModelInstanceId(knowledgeBase.getEmbeddingModelInstanceId());
        version.setEmbeddingDimension(candidate.dimension());
        version.setDistanceType(DISTANCE_TYPE);
        version.setContentChecksum(candidate.checksum());
        version.setChunkCount(candidate.chunkCount());
        version.setPublishedBy(operatorId);
        version.setPublishedAt(OffsetDateTime.now());
        List<KnowledgeIndexVersionTaskEntity> references = candidate.documents().stream()
                .map(document -> indexTask(document.getId(), document.getActiveChunkTaskId()))
                .toList();
        indexVersionRepo.insert(version, references);
        return version;
    }

    private static void validateTask(KnowledgeBaseEntity knowledgeBase, Long embeddingModelInstanceId, KnowledgeChunkTaskEntity task) {
        boolean complete = task.getStatus() == KnowledgeChunkTaskStatus.SUCCEEDED
                && Objects.equals(task.getKnowledgeBaseId(), knowledgeBase.getId())
                && Objects.equals(task.getEmbeddingModelInstanceId(), embeddingModelInstanceId)
                && Objects.nonNull(task.getChunkCount())
                && task.getChunkCount() > 0
                && Objects.equals(task.getChunkCount(), task.getVectorizedCount());
        if (!complete) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库存在未完成或模型不一致的生效分块任务");
        }
    }

    private static KnowledgeIndexVersionTaskEntity indexTask(Long documentId, Long taskId) {
        KnowledgeIndexVersionTaskEntity reference = new KnowledgeIndexVersionTaskEntity();
        reference.setDocumentId(documentId);
        reference.setChunkTaskId(taskId);
        return reference;
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long id) {
        return knowledgeBaseRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
    }

    private static String checksum(List<KnowledgeChunkTaskEntity> tasks) {
        StringBuilder source = new StringBuilder();
        for (KnowledgeChunkTaskEntity task : tasks) {
            source.append(task.getId()).append(':')
                    .append(task.getVersionNo()).append(':')
                    .append(task.getChunkCount()).append(':')
                    .append(task.getTotalTokens()).append(':')
                    .append(task.getUpdatedAt()).append(';');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256算法", exception);
        }
    }

    /**
     * @param documents  当前生效文档及其任务引用
     * @param dimension  统一向量维度
     * @param chunkCount 索引包含的Chunk数量
     * @param checksum   内容集合校验和
     */
    private record IndexCandidate(List<KnowledgeDocumentEntity> documents, int dimension, int chunkCount,
                                  String checksum) {
    }
}
