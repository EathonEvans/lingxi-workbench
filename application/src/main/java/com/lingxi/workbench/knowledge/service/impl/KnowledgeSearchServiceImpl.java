package com.lingxi.workbench.knowledge.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkMetadata;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeVectorRepo;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库语义检索服务实现。
 *
 * <p>三步：Query Embedding → pgvector 相似度查询 → Chunk 详情回填。</p>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private final KnowledgeBaseRepo knowledgeBaseRepo;
    private final KnowledgeVectorRepo knowledgeVectorRepo;
    private final KnowledgeChunkRepo knowledgeChunkRepo;
    private final EmbeddingGateway embeddingGateway;

    @Override
    public List<KnowledgeSearchResultVO> search(String query, Long knowledgeBaseId, EmbeddingModelRuntimeConfig embeddingConfig, int topK, double similarityThreshold) {
        requireKnowledgeBase(knowledgeBaseId);
        float[] queryVector = embed(query, embeddingConfig);
        List<KnowledgeVectorRepo.VectorSearchRow> rows = knowledgeVectorRepo.search(queryVector, knowledgeBaseId, topK, similarityThreshold);
        return loadResults(rows);
    }

    @Override
    public List<KnowledgeSearchResultVO> searchVersion(String query, Long knowledgeBaseId, Long indexVersionId, EmbeddingModelRuntimeConfig embeddingConfig, int topK, double similarityThreshold) {
        requireKnowledgeBase(knowledgeBaseId);
        float[] queryVector = embed(query, embeddingConfig);
        List<KnowledgeVectorRepo.VectorSearchRow> rows = knowledgeVectorRepo.searchVersion(
                queryVector, knowledgeBaseId, indexVersionId, embeddingConfig.getInstanceId(), topK, similarityThreshold);
        return loadResults(rows);
    }

    private List<KnowledgeSearchResultVO> loadResults(List<KnowledgeVectorRepo.VectorSearchRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> chunkIds = rows.stream().map(KnowledgeVectorRepo.VectorSearchRow::chunkId).toList();
        Map<Long, KnowledgeChunkEntity> chunkMap = knowledgeChunkRepo.findEnabledByIds(chunkIds).stream().collect(Collectors.toMap(KnowledgeChunkEntity::getId, Function.identity()));
        return rows.stream()
                .map(row -> {
                    KnowledgeChunkEntity chunk = chunkMap.get(row.chunkId());
                    if (Objects.isNull(chunk)) {
                        return null;
                    }
                    KnowledgeChunkMetadata metadata = chunk.getMetadata();
                    return KnowledgeSearchResultVO.builder()
                            .chunkId(row.chunkId())
                            .content(chunk.getContent())
                            .similarity(Math.round(row.similarity() * 10000.0) / 10000.0)
                            .documentName(Objects.nonNull(metadata) ? chunk.getMetadata().getDocumentName() : null)
                            .sectionTitle(Objects.nonNull(metadata) ? chunk.getMetadata().getSectionTitle() : null)
                            .pageNumber(Objects.nonNull(metadata) ? chunk.getMetadata().getPageNumber() : null)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long knowledgeBaseId) {
        return knowledgeBaseRepo.findActiveById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.BAD_REQUEST, "知识库不存在或已停用"));
    }

    private float[] embed(String query, EmbeddingModelRuntimeConfig embeddingConfig) {
        List<float[]> vectors = embeddingGateway.embed(List.of(query), embeddingConfig);
        if (vectors.size() != 1 || Objects.isNull(vectors.getFirst()) || vectors.getFirst().length == 0) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "知识搜索Query向量生成失败");
        }
        return vectors.getFirst();
    }
}
