package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.lingxi.workbench.infra.ai.outbound.config.ModelOutboundSecurityProperties;
import com.lingxi.workbench.infra.ai.rerank.config.RerankHttpProperties;
import com.lingxi.workbench.infra.ai.rerank.http.HttpRerankGateway;
import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeVectorRepo;
import com.lingxi.workbench.knowledge.service.KnowledgeRerankInputBudget;
import com.lingxi.workbench.knowledge.service.impl.KnowledgeSearchServiceImpl;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.observability.service.StageObserver;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 数据文件到报告的协作验收：使用实际检索服务和 HTTP 重排，数据库/Embedding 以固定样本替代。 */
class KnowledgeEvaluationFlowTest {
    @TempDir Path directory;

    @Test
    void shouldEvaluateBoundedRerankingAndDeniedIdentityThroughTheSameSearchService() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"results\":[{\"index\":1,\"relevance_score\":0.95}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            KnowledgeBaseRepo bases = mock(KnowledgeBaseRepo.class);
            KnowledgeVectorRepo vectors = mock(KnowledgeVectorRepo.class);
            KnowledgeChunkRepo chunks = mock(KnowledgeChunkRepo.class);
            EmbeddingGateway embedding = mock(EmbeddingGateway.class);
            ModelRuntimeConfigService models = mock(ModelRuntimeConfigService.class);
            PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            when(bases.canRead(1L, 12L)).thenReturn(true);
            when(bases.findIndexDimension(1L, 81L, 7L)).thenReturn(Optional.of(2));
            when(models.getEmbeddingModelConfig(7L)).thenReturn(EmbeddingModelRuntimeConfig.builder()
                    .instanceId(7L).timeoutSeconds(5).maxRetries(0).build());
            when(embedding.embed(anyList(), any())).thenReturn(List.of(new float[]{0.1F, 0.2F}));
            when(vectors.searchVersion(any(), eq(1L), eq(81L), eq(7L), eq(3), eq(0.6))).thenReturn(List.of(
                    new KnowledgeVectorRepo.VectorSearchRow(10L, 0.9), new KnowledgeVectorRepo.VectorSearchRow(20L, 0.8),
                    new KnowledgeVectorRepo.VectorSearchRow(30L, 0.7)));
            when(chunks.findEnabledByIds(List.of(10L, 20L, 30L), 1L)).thenReturn(List.of(chunk(10L), chunk(20L), chunk(30L)));
            when(models.getRerankModelConfig(9L)).thenReturn(RerankModelRuntimeConfig.builder().instanceId(9L)
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .apiKey("test-only-key").modelCode("fixture").timeoutSeconds(5).maxRetries(0).build());
            ModelOutboundSecurityProperties security = new ModelOutboundSecurityProperties();
            security.setAllowedPrivateHosts(List.of("127.0.0.1"));
            KnowledgeRetrievalProperties limits = new KnowledgeRetrievalProperties();
            limits.setMaxDocumentTokens(32);
            limits.setMaxRerankInputTokens(128);
            TokenCountEstimator estimator = mock(TokenCountEstimator.class);
            when(estimator.estimate(anyString())).thenAnswer(call -> call.<String>getArgument(0).length());
            KnowledgeSearchServiceImpl search = new KnowledgeSearchServiceImpl(bases, vectors, chunks, embedding, models,
                    new HttpRerankGateway(new ModelOutboundAddressPolicy(security), new RerankHttpProperties()),
                    new KnowledgeRerankInputBudget(limits, estimator), limits, transactions, new StageObserver(ObservationRegistry.create()));
            Path dataset = directory.resolve("dataset.json");
            Files.writeString(dataset, """
                    {"datasetVersion":"fixture-v1","samples":[
                      {"id":"positive","request":{"query":"qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq","userId":12,
                        "knowledgeBaseId":1,"indexVersionId":81,"embeddingModelInstanceId":7,
                        "topK":1,"similarityThreshold":0.6,"timeoutSeconds":10,
                        "rerank":{"modelInstanceId":9,"candidateTopK":3,"minScore":0.5}},
                       "expectedChunkIds":[20],"expectDenied":false},
                      {"id":"denied","request":{"query":"private query","userId":99,
                        "knowledgeBaseId":1,"indexVersionId":81,"embeddingModelInstanceId":7,
                        "topK":1,"similarityThreshold":0.6,"timeoutSeconds":10},
                       "expectedChunkIds":[],"expectDenied":true}
                    ]}
                    """);
            Path output = directory.resolve("report.json");
            KnowledgeEvaluationReport report = new KnowledgeEvaluationCommand(new KnowledgeEvaluationService(search, factory.getValidator()))
                    .execute(new String[]{"--dataset=" + dataset, "--output=" + output});
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getVectorMetrics().recallAtK()).isZero();
            assertThat(report.getFinalMetrics().recallAtK()).isEqualTo(1);
            assertThat(report.getFinalMetrics().mrr()).isEqualTo(1);
            assertThat(report.getSamples().getFirst().getDiagnostics()).satisfies(diagnostics -> {
                assertThat(diagnostics.getTruncatedDocuments()).isEqualTo(2);
                assertThat(diagnostics.getDroppedDocuments()).isEqualTo(1);
                assertThat(diagnostics.getEstimatedRerankTokens()).isEqualTo(128);
            });
            assertThat(calls.get()).isEqualTo(1);
            assertThat(body.get()).contains("\"documents\":[\"" + "d".repeat(32) + "\",\"" + "d".repeat(32) + "\"]");
            assertThat(Files.readString(output)).contains("403").doesNotContain("private query", "test-only-key", "d".repeat(32));
            verify(embedding, times(1)).embed(anyList(), any());
        } finally {
            server.stop(0);
        }
    }

    private static KnowledgeChunkEntity chunk(Long id) {
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(id);
        chunk.setContent("d".repeat(64));
        return chunk;
    }
}
