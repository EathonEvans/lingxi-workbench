package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DETERMINISTIC_READ_ONLY;

/** knowledge.search 的服务端锁定参数与结果预算测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchAgentToolFactoryTest {
    @Mock private KnowledgeSearchService knowledgeSearchService;
    @Mock private ModelRuntimeConfigService modelRuntimeConfigService;
    @Mock private TokenCountEstimator tokenCountEstimator;

    private KnowledgeSearchAgentToolFactory factory;

    @BeforeEach
    void setUp() {
        factory = new KnowledgeSearchAgentToolFactory(knowledgeSearchService, modelRuntimeConfigService, tokenCountEstimator);
    }

    @Test
    void shouldUseFrozenResourceIdentifiersAndHideThemFromModelInput() {
        AgentRuntimeDefinition.KnowledgeSearchCapability capability = capability();
        prepareTokenEstimator();
        when(modelRuntimeConfigService.getEmbeddingModelConfig(7L)).thenReturn(embeddingConfig());
        when(knowledgeSearchService.searchVersion("退款条件", 1L, 81L, embeddingConfig(), 5, 0.65D))
                .thenReturn(List.of(result("支持七天退款")));

        AgentTool tool = factory.create(capability);
        String output = tool.callback().call("{\"query\":\"退款条件\"}");

        assertThat(tool.callback().getToolDefinition().inputSchema())
                .contains("query")
                .doesNotContain("knowledgeBaseId", "knowledgeIndexVersionId", "embeddingModelInstanceId");
        assertThat(tool.definition().getExecutionMode()).isEqualTo(DETERMINISTIC_READ_ONLY);
        assertThat(output).contains("支持七天退款", "产品手册", "第3页");
        verify(knowledgeSearchService).searchVersion("退款条件", 1L, 81L, embeddingConfig(), 5, 0.65D);
    }

    @Test
    void shouldRejectQueryLongerThanPublishedLimit() {
        AgentRuntimeDefinition.KnowledgeSearchCapability capability = capability();
        capability.setMaxQueryCharacters(4);
        AgentTool tool = factory.create(capability);

        assertThatThrownBy(() -> tool.callback().call("{\"query\":\"12345\"}"))
                .hasRootCauseMessage("knowledge.search查询超过字符上限");
    }

    @Test
    void shouldTruncateStructuredResultsByTokenBudget() {
        AgentRuntimeDefinition.KnowledgeSearchCapability capability = capability();
        capability.setMaxResultTokens(3);
        prepareTokenEstimator();
        when(modelRuntimeConfigService.getEmbeddingModelConfig(7L)).thenReturn(embeddingConfig());
        when(knowledgeSearchService.searchVersion("query", 1L, 81L, embeddingConfig(), 5, 0.65D))
                .thenReturn(List.of(result("abcdef"), result("second")));

        String output = factory.create(capability).callback().call("{\"query\":\"query\"}");

        assertThat(output).contains("\"content\":\"abc\"", "\"truncated\":true");
        assertThat(output).doesNotContain("abcdef", "second");
    }

    @Test
    void shouldReportSaturatedWhenDifferentQueriesReturnNoNewEvidence() {
        prepareTokenEstimator();
        when(modelRuntimeConfigService.getEmbeddingModelConfig(7L)).thenReturn(embeddingConfig());
        when(knowledgeSearchService.searchVersion(anyString(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(81L), org.mockito.ArgumentMatchers.eq(embeddingConfig()),
                org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.eq(0.65D)))
                .thenReturn(List.of(result("同一条知识")));

        AgentTool tool = factory.create(capability());
        String first = tool.callback().call("{\"query\":\"退款条件\"}");
        String second = tool.callback().call("{\"query\":\"退款政策\"}");

        assertThat(first).contains("\"status\":\"FOUND\"", "\"newEvidenceCount\":1");
        assertThat(second).contains("\"status\":\"SATURATED\"", "\"newEvidenceCount\":0", "停止调用知识搜索");
    }

    @Test
    void shouldAllowOneEmptyQueryRewriteBeforeReportingSaturated() {
        when(modelRuntimeConfigService.getEmbeddingModelConfig(7L)).thenReturn(embeddingConfig());
        when(knowledgeSearchService.searchVersion(anyString(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(81L), org.mockito.ArgumentMatchers.eq(embeddingConfig()),
                org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.eq(0.65D)))
                .thenReturn(List.of());

        AgentTool tool = factory.create(capability());
        String first = tool.callback().call("{\"query\":\"不存在的信息\"}");
        String second = tool.callback().call("{\"query\":\"换一种说法\"}");

        assertThat(first).contains("\"status\":\"EMPTY\"");
        assertThat(second).contains("\"status\":\"SATURATED\"", "停止调用知识搜索");
    }

    private void prepareTokenEstimator() {
        when(tokenCountEstimator.estimate(anyString())).thenAnswer(invocation -> invocation.<String>getArgument(0).length());
    }

    private static AgentRuntimeDefinition.KnowledgeSearchCapability capability() {
        return AgentRuntimeDefinition.KnowledgeSearchCapability.builder()
                .knowledgeBaseId(1L)
                .knowledgeIndexVersionId(81L)
                .embeddingModelInstanceId(7L)
                .topK(5)
                .similarityThreshold(new BigDecimal("0.65"))
                .maxResultTokens(100)
                .maxQueryCharacters(100)
                .timeoutSeconds(8)
                .build();
    }

    private static EmbeddingModelRuntimeConfig embeddingConfig() {
        return EmbeddingModelRuntimeConfig.builder()
                .instanceId(7L)
                .baseUrl("http://localhost")
                .apiKey("secret")
                .modelCode("embedding")
                .timeoutSeconds(8)
                .maxRetries(1)
                .build();
    }

    private static KnowledgeSearchResultVO result(String content) {
        return KnowledgeSearchResultVO.builder()
                .chunkId(10L)
                .content(content)
                .similarity(0.9D)
                .documentName("产品手册")
                .pageNumber(3)
                .build();
    }
}
