package com.lingxi.workbench.knowledge.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.RerankGateway;
import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.model.RerankResult;
import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchDiagnostics;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchHit;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchResponse;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkMetadata;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeVectorRepo;
import com.lingxi.workbench.knowledge.service.KnowledgeRerankInputBudget;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.service.StageObserver;
import io.micrometer.observation.Observation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 知识库语义检索服务实现。
 *
 * <p>当前用户授权 → 固定版本向量召回 → 授权库内正文回填 → 可选重排。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private final KnowledgeBaseRepo knowledgeBaseRepo;
    private final KnowledgeVectorRepo knowledgeVectorRepo;
    private final KnowledgeChunkRepo knowledgeChunkRepo;
    private final EmbeddingGateway embeddingGateway;

    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final RerankGateway rerankGateway;
    private final KnowledgeRerankInputBudget inputBudget;
    private final KnowledgeRetrievalProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final StageObserver stageObserver;

    @Override
    public KnowledgeSearchResponse searchVersion(KnowledgeSearchRequest request) {
        return stageObserver.observe(AiObservationStage.KNOWLEDGE_SEARCH, (observation, response) -> {
            recordRanks(observation, "lingxi.knowledge.candidates", response.diagnostics().getCandidates());
            recordRanks(observation, "lingxi.knowledge.selected", response.diagnostics().getSelected());
            observation.highCardinalityKeyValue("lingxi.knowledge.truncated", Integer.toString(response.diagnostics().getTruncatedDocuments()))
                    .highCardinalityKeyValue("lingxi.knowledge.dropped", Integer.toString(response.diagnostics().getDroppedDocuments()))
                    .highCardinalityKeyValue("lingxi.knowledge.estimated_tokens", Integer.toString(response.diagnostics().getEstimatedRerankTokens()))
                    .highCardinalityKeyValue("lingxi.knowledge.base.id", request.getKnowledgeBaseId().toString())
                    .highCardinalityKeyValue("lingxi.knowledge.index.id", request.getIndexVersionId().toString())
                    .highCardinalityKeyValue("lingxi.knowledge.top_k", Integer.toString(request.getTopK()))
                    .highCardinalityKeyValue("lingxi.knowledge.threshold", Double.toString(request.getSimilarityThreshold()));
            if (Objects.nonNull(request.getExecutionId())) observation.highCardinalityKeyValue("lingxi.run.id", request.getExecutionId());
        }, () -> search(request));
    }

    private KnowledgeSearchResponse search(KnowledgeSearchRequest request) {
        long startedAt = System.nanoTime();
        Map<AiObservationStage, Long> stageMillis = new EnumMap<>(AiObservationStage.class);
        int dimension = observe(request, AiObservationStage.KNOWLEDGE_AUTHORIZATION, stageMillis, () ->
                readWithinDeadline(request, startedAt, () -> {
                    // 授权必须先于模型配置、向量和正文读取，拒绝信息不暴露资源存在性。
                    if (Objects.isNull(request.getUserId()) || !knowledgeBaseRepo.canRead(request.getKnowledgeBaseId(), request.getUserId())) {
                        throw new BusinessException(HttpStatusEnum.FORBIDDEN, "当前用户无权检索该知识库");
                    }
                    return knowledgeBaseRepo.findIndexDimension(request.getKnowledgeBaseId(), request.getIndexVersionId(),
                            request.getEmbeddingModelInstanceId()).orElseThrow(() ->
                            new BusinessException(HttpStatusEnum.CONFLICT, "知识索引版本与知识库或Embedding模型不一致"));
                }));
        float[] queryVector = observe(request, AiObservationStage.KNOWLEDGE_EMBEDDING, stageMillis, () -> {
            EmbeddingModelRuntimeConfig source = readWithinDeadline(request, startedAt, () ->
                    modelRuntimeConfigService.getEmbeddingModelConfig(request.getEmbeddingModelInstanceId()));
            // 在线检索采用单次向量请求，避免 SDK 内部重试/退避越过本轮总时限。
            EmbeddingModelRuntimeConfig config = EmbeddingModelRuntimeConfig.builder()
                    .instanceId(source.getInstanceId()).baseUrl(source.getBaseUrl()).apiKey(source.getApiKey())
                    .modelCode(source.getModelCode()).maxRetries(0)
                    .timeoutSeconds(Math.min(source.getTimeoutSeconds(), remainingSeconds(startedAt, request.getTimeoutSeconds())))
                    .build();
            float[] vector = embed(request.getQuery(), config);
            if (vector.length != dimension) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "Query向量维度与发布索引不一致");
            }
            return vector;
        });
        List<KnowledgeSearchResultVO> candidates = observe(request, AiObservationStage.KNOWLEDGE_RECALL, stageMillis, () ->
                readWithinDeadline(request, startedAt, () -> {
                    int candidateTopK = Objects.isNull(request.getRerank()) ? request.getTopK() : request.getRerank().candidateTopK();
                    List<KnowledgeVectorRepo.VectorSearchRow> rows = knowledgeVectorRepo.searchVersion(queryVector,
                            request.getKnowledgeBaseId(), request.getIndexVersionId(), request.getEmbeddingModelInstanceId(),
                            candidateTopK, request.getSimilarityThreshold());
                    return loadResults(rows, request.getKnowledgeBaseId());
                }));
        List<KnowledgeSearchHit> candidateHits = hits(candidates);
        KnowledgeSearchDiagnostics.KnowledgeSearchDiagnosticsBuilder diagnostics = KnowledgeSearchDiagnostics.builder()
                .candidates(candidateHits);
        List<KnowledgeSearchResultVO> selected = candidates;
        if (!candidates.isEmpty() && Objects.nonNull(request.getRerank())) {
            selected = observe(request, AiObservationStage.KNOWLEDGE_RERANK, stageMillis, () -> {
                KnowledgeRerankInputBudget.Selection input = inputBudget.select(request.getQuery(), candidates);
                diagnostics.truncatedDocuments(input.truncatedDocuments()).droppedDocuments(input.droppedDocuments())
                        .estimatedRerankTokens(input.estimatedTokens());
                RerankModelRuntimeConfig source = readWithinDeadline(request, startedAt, () ->
                        modelRuntimeConfigService.getRerankModelConfig(request.getRerank().modelInstanceId()));
                RerankModelRuntimeConfig config = source.toBuilder()
                        .timeoutSeconds(Math.min(source.getTimeoutSeconds(), remainingSeconds(startedAt, request.getTimeoutSeconds())))
                        .build();
                List<RerankResult> ranked = rerankGateway.rerank(request.getQuery(), input.documents(), request.getTopK(), config);
                return ranked.stream().filter(result -> result.score() >= request.getRerank().minScore())
                        .map(result -> {
                            KnowledgeSearchResultVO candidate = candidates.get(result.index());
                            candidate.setRerankScore(result.score());
                            return candidate;
                        }).toList();
            });
        }
        remainingSeconds(startedAt, request.getTimeoutSeconds());
        return new KnowledgeSearchResponse(selected, diagnostics.selected(hits(selected)).stageMillis(Map.copyOf(stageMillis))
                .totalMillis(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()).build());
    }

    private <T> T readWithinDeadline(KnowledgeSearchRequest request, long startedAt, Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(Math.min(properties.getDatabaseTimeoutSeconds(), remainingSeconds(startedAt, request.getTimeoutSeconds())));
        // MyBatis 和 JdbcTemplate 将当前事务剩余时限应用到 JDBC Statement；不修改共享 JdbcTemplate。
        return transaction.execute(status -> action.get());
    }

    private <T> T observe(KnowledgeSearchRequest request, AiObservationStage stage, Map<AiObservationStage, Long> stageMillis, Supplier<T> action) {
        long startedAt = System.nanoTime();
        try {
            return stageObserver.observe(stage, (observation, result) -> {
                observation.highCardinalityKeyValue("lingxi.knowledge.base.id", request.getKnowledgeBaseId().toString())
                        .highCardinalityKeyValue("lingxi.knowledge.index.id", request.getIndexVersionId().toString());
                if (Objects.nonNull(request.getExecutionId())) {
                    observation.highCardinalityKeyValue("lingxi.run.id", request.getExecutionId());
                }
            }, () -> {
                try {
                    return action.get();
                } catch (BusinessException exception) {
                    logFailure(request, stage, exception);
                    throw exception;
                } catch (RuntimeException exception) {
                    logFailure(request, stage, exception);
                    // 供应商和 JDBC 异常可能含原始正文；只向 Trace 暴露阶段，不上传原始异常消息。
                    throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "知识检索阶段失败：" + stage.getCode());
                }
            });
        } finally {
            stageMillis.put(stage, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }
    }

    /** 工具失败可能被模型处理后正常结束回答，必须在检索边界留下不含查询和供应商正文的诊断。 */
    private static void logFailure(KnowledgeSearchRequest request, AiObservationStage stage, RuntimeException exception) {
        int code = exception instanceof BusinessException business ? business.getCode() : HttpStatusEnum.SERVICE_UNAVAILABLE.getCode();
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        String sqlState = cause instanceof SQLException sql ? sql.getSQLState() : null;
        StackTraceElement[] frames = cause.getStackTrace();
        String location = frames.length == 0 ? "unknown" : frames[0].toString();
        log.atLevel(code < 500 ? Level.WARN : Level.ERROR)
                .log("知识检索失败: stage={}, runId={}, userId={}, knowledgeBaseId={}, indexVersionId={}, code={}, causeType={}, sqlState={}, location={}",
                        stage.getCode(), request.getExecutionId(), request.getUserId(), request.getKnowledgeBaseId(), request.getIndexVersionId(),
                        code, cause.getClass().getName(), sqlState, location);
    }

    /** 平台 OTLP 单属性最多 256 字符，按完整片段记录分段，避免整段 JSON 被截掉。 */
    private static void recordRanks(Observation observation, String key, List<KnowledgeSearchHit> hits) {
        StringBuilder page = new StringBuilder();
        int pageNumber = 0;
        for (KnowledgeSearchHit hit : hits) {
            String score = Objects.isNull(hit.rerankScore()) ? "-" : String.format(Locale.ROOT, "%.4f", hit.rerankScore());
            String entry = String.format(Locale.ROOT, "%d/%.4f/%s;", hit.chunkId(), hit.similarity(), score);
            if (page.length() + entry.length() > 240) {
                observation.highCardinalityKeyValue(key + "." + pageNumber++, page.toString());
                page.setLength(0);
            }
            page.append(entry);
        }
        if (!page.isEmpty()) observation.highCardinalityKeyValue(key + "." + pageNumber, page.toString());
    }

    private static List<KnowledgeSearchHit> hits(List<KnowledgeSearchResultVO> results) {
        return results.stream().map(result -> new KnowledgeSearchHit(result.getChunkId(), result.getSimilarity(), result.getRerankScore())).toList();
    }

    private static int remainingSeconds(long startedAt, int timeoutSeconds) {
        long remainingNanos = Duration.ofSeconds(timeoutSeconds).toNanos() - (System.nanoTime() - startedAt);
        if (remainingNanos <= 0) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "知识检索已超过执行时限");
        }
        // JDBC / Embedding 的配置粒度为秒；不足一秒时仍使用最小正超时，并在阶段结束后复核总时限。
        return Math.max(1, (int) Duration.ofNanos(remainingNanos).toSeconds());
    }

    private List<KnowledgeSearchResultVO> loadResults(List<KnowledgeVectorRepo.VectorSearchRow> rows, Long knowledgeBaseId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> chunkIds = rows.stream().map(KnowledgeVectorRepo.VectorSearchRow::chunkId).distinct().toList();
        Map<Long, KnowledgeChunkEntity> chunkMap = knowledgeChunkRepo.findEnabledByIds(chunkIds, knowledgeBaseId).stream().collect(Collectors.toMap(KnowledgeChunkEntity::getId, Function.identity()));
        return rows.stream().distinct()
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

    private float[] embed(String query, EmbeddingModelRuntimeConfig embeddingConfig) {
        List<float[]> vectors = embeddingGateway.embed(List.of(query), embeddingConfig);
        if (vectors.size() != 1 || Objects.isNull(vectors.getFirst()) || vectors.getFirst().length == 0) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "知识搜索Query向量生成失败");
        }
        return vectors.getFirst();
    }
}
