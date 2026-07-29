package com.lingxi.workbench.knowledge.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
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
    public List<KnowledgeSearchResultVO> search(
            String query,
            Long knowledgeBaseId,
            EmbeddingModelRuntimeConfig embeddingConfig,
            int topK,
            double similarityThreshold) {
        // 1. 校验知识库存在且启用
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepo.findActiveById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.BAD_REQUEST, "知识库不存在或已停用"));

        // 2. Query Embedding
        float[] queryVector = embeddingGateway.embed(List.of(query), embeddingConfig).get(0);

        // 3. pgvector 相似度检索
        List<KnowledgeVectorRepo.VectorSearchRow> rows = knowledgeVectorRepo.search(
                queryVector, knowledgeBaseId, topK, similarityThreshold);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 4. Chunk 详情回填
        List<Long> chunkIds = rows.stream().map(KnowledgeVectorRepo.VectorSearchRow::chunkId).toList();
        Map<Long, KnowledgeChunkEntity> chunkMap = knowledgeChunkRepo.findEnabledByIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeChunkEntity::getId, Function.identity()));

        // 5. 按 pgvector 排序组装
        List<KnowledgeSearchResultVO> results = new ArrayList<>(rows.size());
        for (KnowledgeVectorRepo.VectorSearchRow row : rows) {
            KnowledgeChunkEntity chunk = chunkMap.get(row.chunkId());
            if (chunk == null) {
                continue;
            }
            results.add(KnowledgeSearchResultVO.builder()
                    .chunkId(row.chunkId())
                    .content(chunk.getContent())
                    .similarity(Math.round(row.similarity() * 10000.0) / 10000.0)
                    .documentName(chunk.getMetadata() != null ? chunk.getMetadata().getDocumentName() : null)
                    .sectionTitle(chunk.getMetadata() != null ? chunk.getMetadata().getSectionTitle() : null)
                    .pageNumber(chunk.getMetadata() != null ? chunk.getMetadata().getPageNumber() : null)
                    .build());
        }
        return results;
    }
}
