package com.lingxi.workbench.knowledge.service.impl;

import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.RerankGateway;
import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeVectorRepo;
import com.lingxi.workbench.knowledge.service.KnowledgeRerankInputBudget;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.observability.service.StageObserver;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 Spring JDBC 事务和生产向量 Repo，验证时限确实下沉到 Statement。 */
class KnowledgeRetrievalTransactionTest {
    @Test
    void statementMustHaveTimeoutWhileEmbeddingRunsOutsideTransaction() throws Exception {
        Fixture fixture = fixture();
        ResultSet rows = mock(ResultSet.class);
        when(fixture.statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("chunk_id")).thenReturn(10L);
        when(rows.getDouble("similarity")).thenReturn(0.9);
        assertThat(fixture.service.searchVersion(request()).results()).hasSize(1);
        verify(fixture.statement).setQueryTimeout(intThat(seconds -> seconds > 0 && seconds <= 2));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void queryTimeoutMustFailWithoutRerankOrLeakingSqlDetails() throws Exception {
        Fixture fixture = fixture();
        when(fixture.statement.executeQuery()).thenThrow(new SQLTimeoutException("private database details"));
        assertThatThrownBy(() -> fixture.service.searchVersion(request())).hasMessage("知识检索阶段失败：KNOWLEDGE_RECALL");
        verifyNoInteractions(fixture.rerank);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static Fixture fixture() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        KnowledgeBaseRepo bases = mock(KnowledgeBaseRepo.class);
        when(bases.canRead(1L, 12L)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return true;
        });
        when(bases.findIndexDimension(1L, 81L, 7L)).thenReturn(Optional.of(2));
        KnowledgeChunkRepo chunks = mock(KnowledgeChunkRepo.class);
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(10L);
        chunk.setContent("text");
        when(chunks.findEnabledByIds(List.of(10L), 1L)).thenReturn(List.of(chunk));
        ModelRuntimeConfigService models = mock(ModelRuntimeConfigService.class);
        when(models.getEmbeddingModelConfig(7L)).thenReturn(EmbeddingModelRuntimeConfig.builder()
                .instanceId(7L).timeoutSeconds(10).maxRetries(2).build());
        EmbeddingGateway embedding = mock(EmbeddingGateway.class);
        when(embedding.embed(anyList(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            EmbeddingModelRuntimeConfig config = call.getArgument(1);
            assertThat(config.getMaxRetries()).isZero();
            return List.of(new float[]{0.1F, 0.2F});
        });
        RerankGateway rerank = mock(RerankGateway.class);
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setDatabaseTimeoutSeconds(2);
        KnowledgeSearchServiceImpl service = new KnowledgeSearchServiceImpl(bases, new KnowledgeVectorRepo(new JdbcTemplate(source)),
                chunks, embedding, models, rerank, mock(KnowledgeRerankInputBudget.class), properties,
                new DataSourceTransactionManager(source), new StageObserver(ObservationRegistry.NOOP));
        return new Fixture(service, statement, rerank);
    }

    private static KnowledgeSearchRequest request() {
        return KnowledgeSearchRequest.builder().query("query").knowledgeBaseId(1L).userId(12L).indexVersionId(81L)
                .embeddingModelInstanceId(7L).topK(2).similarityThreshold(0.5).timeoutSeconds(10).build();
    }

    /**
     * JDBC 边界测试依赖。
     * @param service 生产检索服务
     * @param statement 可模拟慢查询的 JDBC Statement
     * @param rerank 重排序外部边界
     */
    private record Fixture(KnowledgeSearchServiceImpl service, PreparedStatement statement, RerankGateway rerank) { }
}
