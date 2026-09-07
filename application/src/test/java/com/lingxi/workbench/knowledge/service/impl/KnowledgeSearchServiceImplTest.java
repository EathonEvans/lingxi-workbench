package com.lingxi.workbench.knowledge.service.impl;

import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.RerankGateway;
import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.model.RerankPolicy;
import com.lingxi.workbench.infra.ai.rerank.model.RerankResult;
import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchResponse;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkMetadata;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeVectorRepo;
import com.lingxi.workbench.knowledge.service.KnowledgeRerankInputBudget;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.observability.service.StageObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证授权先行、固定索引契约和两阶段排序的真实协作边界。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceImplTest {
    @Mock private KnowledgeBaseRepo bases;
    @Mock private KnowledgeVectorRepo vectors;
    @Mock private KnowledgeChunkRepo chunks;
    @Mock private EmbeddingGateway embedding;
    @Mock private ModelRuntimeConfigService models;
    @Mock private RerankGateway rerank;
    private KnowledgeSearchServiceImpl service;
    private final List<Observation.Context> observations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        lenient().when(estimator.estimate(anyString())).thenAnswer(call -> call.<String>getArgument(0).length());
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override public boolean supportsContext(Observation.Context context) { return true; }
            @Override public void onStop(Observation.Context context) { observations.add(context); }
        });
        service = new KnowledgeSearchServiceImpl(bases, vectors, chunks, embedding, models, rerank,
                new KnowledgeRerankInputBudget(properties, estimator), properties, manager, new StageObserver(registry));
    }

    @Test
    void deniedUserMustNotLoadModelsVectorsOrPrivateContent() {
        assertThatThrownBy(() -> service.searchVersion(request(true))).hasMessageContaining("无权检索");
        verifyNoInteractions(models, embedding, vectors, chunks, rerank);
        verify(bases, never()).findIndexDimension(any(), any(), any());
    }

    @Test
    void mismatchedIndexMustFailBeforeExternalCalls() {
        when(bases.canRead(1L, 12L)).thenReturn(true);
        assertThatThrownBy(() -> service.searchVersion(request(true))).hasMessageContaining("索引版本");
        verifyNoInteractions(models, embedding, vectors, chunks, rerank);
    }

    @Test
    void dimensionMismatchMustNotSearchVectors() {
        prepareEmbedding(3);
        assertThatThrownBy(() -> service.searchVersion(request(true))).hasMessageContaining("向量维度");
        verifyNoInteractions(vectors, chunks, rerank);
    }

    @Test
    void rerankMustReorderCandidatesAndPreserveSourceAndBothScores() {
        prepareCandidates(true);
        when(models.getRerankModelConfig(9L)).thenReturn(rerankConfig());
        when(rerank.rerank(eq("问题"), eq(List.of("片段一", "片段二")), eq(2), any()))
                .thenReturn(List.of(new RerankResult(1, 0.95), new RerankResult(0, 0.8)));
        KnowledgeSearchResponse response = service.searchVersion(request(true));
        List<KnowledgeSearchResultVO> result = response.results();
        assertThat(response.diagnostics().getCandidates()).extracting("chunkId").containsExactly(10L, 20L);
        assertThat(response.diagnostics().getSelected()).extracting("chunkId").containsExactly(20L, 10L);
        assertThat(result).extracting(KnowledgeSearchResultVO::getChunkId).containsExactly(20L, 10L);
        assertThat(result.getFirst()).extracting("similarity", "rerankScore", "documentName", "pageNumber")
                .containsExactly(0.7, 0.95, "文档二", 2);
        verify(vectors).searchVersion(any(), eq(1L), eq(81L), eq(7L), eq(10), eq(0.6));
        verify(chunks).findEnabledByIds(List.of(10L, 20L), 1L);
    }

    @Test
    void rerankThresholdMustNotUseVectorSimilarity() {
        prepareCandidates(true);
        when(models.getRerankModelConfig(9L)).thenReturn(rerankConfig());
        when(rerank.rerank(anyString(), anyList(), anyInt(), any()))
                .thenReturn(List.of(new RerankResult(1, 0.9), new RerankResult(0, 0.3)));
        assertThat(service.searchVersion(request(true)).results()).extracting(KnowledgeSearchResultVO::getChunkId).containsExactly(20L);
    }

    @Test
    void disabledRerankMustUseFinalTopKAndVectorOrder() {
        prepareCandidates(false);
        assertThat(service.searchVersion(request(false)).results()).extracting(KnowledgeSearchResultVO::getChunkId).containsExactly(10L, 20L);
        verifyNoInteractions(rerank);
        verify(models, never()).getRerankModelConfig(any());
    }

    @Test
    void emptyRecallMustNotCallReranker() {
        prepareEmbedding(2);
        assertThat(service.searchVersion(request(true)).results()).isEmpty();
        verifyNoInteractions(chunks, rerank);
        verify(models, never()).getRerankModelConfig(any());
    }

    @Test
    void rerankFailureMustNotReturnVectorResultsAsSuccess() {
        prepareCandidates(true);
        when(models.getRerankModelConfig(9L)).thenReturn(rerankConfig());
        when(rerank.rerank(anyString(), anyList(), anyInt(), any())).thenThrow(new IllegalStateException("rerank unavailable"));
        assertThatThrownBy(() -> service.searchVersion(request(true))).hasMessage("知识检索阶段失败：KNOWLEDGE_RERANK");
    }

    @Test
    void diagnosticsMustRecordRankChangesWithoutQueryOrContent() {
        prepareCandidates(true);
        when(models.getRerankModelConfig(9L)).thenReturn(rerankConfig());
        when(rerank.rerank(anyString(), anyList(), anyInt(), any())).thenReturn(List.of(new RerankResult(1, 0.9), new RerankResult(0, 0.8)));
        service.searchVersion(request(true).toBuilder().executionId("run-test").build());
        Observation.Context root = observations.stream().filter(context -> context.getLowCardinalityKeyValue("lingxi.stage").getValue().equals("KNOWLEDGE_SEARCH")).findFirst().orElseThrow();
        assertThat(root.getHighCardinalityKeyValue("lingxi.run.id").getValue()).isEqualTo("run-test");
        assertThat(root.getHighCardinalityKeyValue("lingxi.knowledge.candidates.0").getValue()).startsWith("10/");
        assertThat(root.getHighCardinalityKeyValue("lingxi.knowledge.selected.0").getValue()).startsWith("20/");
        assertThat(root.getHighCardinalityKeyValues()).allSatisfy(value -> {
            assertThat(value.getValue()).hasSizeLessThanOrEqualTo(256).doesNotContain("问题", "片段一", "secret");
        });
    }

    @Test
    void deniedLookupMustLeaveErrorSpanWithoutCandidateData() {
        assertThatThrownBy(() -> service.searchVersion(request(true))).hasMessageContaining("无权检索");
        assertThat(observations).anySatisfy(context -> assertThat(context.getError()).isNotNull());
        assertThat(observations).allSatisfy(context -> assertThat(context.getHighCardinalityKeyValue("lingxi.knowledge.candidates.0")).isNull());
    }

    private void prepareEmbedding(int dimension) {
        when(bases.canRead(1L, 12L)).thenReturn(true);
        when(bases.findIndexDimension(1L, 81L, 7L)).thenReturn(Optional.of(dimension));
        when(models.getEmbeddingModelConfig(7L)).thenReturn(EmbeddingModelRuntimeConfig.builder()
                .instanceId(7L).baseUrl("https://example.com/v1").apiKey("secret").modelCode("embedding")
                .timeoutSeconds(10).maxRetries(0).build());
        when(embedding.embed(eq(List.of("问题")), any())).thenReturn(List.of(new float[]{0.1F, 0.2F}));
    }

    private void prepareCandidates(boolean enabled) {
        prepareEmbedding(2);
        when(vectors.searchVersion(any(), eq(1L), eq(81L), eq(7L), eq(enabled ? 10 : 2), eq(0.6)))
                .thenReturn(List.of(new KnowledgeVectorRepo.VectorSearchRow(10L, 0.9), new KnowledgeVectorRepo.VectorSearchRow(20L, 0.7)));
        when(chunks.findEnabledByIds(List.of(10L, 20L), 1L)).thenReturn(List.of(chunk(10L, "一"), chunk(20L, "二")));
    }

    private static KnowledgeChunkEntity chunk(Long id, String suffix) {
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(id);
        chunk.setContent("片段" + suffix);
        KnowledgeChunkMetadata metadata = new KnowledgeChunkMetadata();
        metadata.setDocumentName("文档" + suffix);
        metadata.setPageNumber(2);
        chunk.setMetadata(metadata);
        return chunk;
    }

    private static RerankModelRuntimeConfig rerankConfig() {
        return RerankModelRuntimeConfig.builder().instanceId(9L).baseUrl("https://example.com/v1")
                .apiKey("secret").modelCode("rerank").timeoutSeconds(10).maxRetries(0).build();
    }

    private static KnowledgeSearchRequest request(boolean rerank) {
        return KnowledgeSearchRequest.builder().query("问题").userId(12L).knowledgeBaseId(1L).indexVersionId(81L)
                .embeddingModelInstanceId(7L).topK(2).similarityThreshold(0.6).timeoutSeconds(10)
                .rerank(rerank ? new RerankPolicy(9L, 10, 0.5) : null).build();
    }
}
