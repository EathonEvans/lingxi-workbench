package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchResponse;
import org.mockito.ArgumentCaptor;
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
    @Mock private TokenCountEstimator tokenCountEstimator;

    private KnowledgeSearchAgentToolFactory factory;

    @BeforeEach
    void setUp() {
        factory = new KnowledgeSearchAgentToolFactory(knowledgeSearchService, tokenCountEstimator);
    }

    @Test
    void shouldUseFrozenResourceIdentifiersAndHideThemFromModelInput() {
        AgentRuntimeDefinition.KnowledgeSearchCapability capability = capability();
        prepareTokenEstimator();
        when(knowledgeSearchService.searchVersion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new KnowledgeSearchResponse(List.of(result("支持七天退款")), null));

        AgentTool tool = factory.create(capability);
        String output = execute(tool, "{\"query\":\"退款条件\"}");

        assertThat(JSON.toJSONString(tool.inputSchema()))
                .contains("query")
                .doesNotContain("knowledgeBaseId", "knowledgeIndexVersionId", "embeddingModelInstanceId");
        assertThat(tool.definition().getExecutionMode()).isEqualTo(DETERMINISTIC_READ_ONLY);
        assertThat(tool.definition().effectiveMaxCallsPerRound()).isEqualTo(2);
        assertThat(tool.definition().effectiveMaxCallsPerExecution()).isEqualTo(2);
        assertThat(output).contains("支持七天退款", "产品手册", "第3页");
        ArgumentCaptor<KnowledgeSearchRequest> request = ArgumentCaptor.forClass(KnowledgeSearchRequest.class);
        verify(knowledgeSearchService).searchVersion(request.capture());
        assertThat(request.getValue()).extracting("query", "userId", "knowledgeBaseId", "indexVersionId", "embeddingModelInstanceId", "topK", "similarityThreshold")
                .containsExactly("退款条件", 12L, 1L, 81L, 7L, 5, 0.65D);
    }

    @Test
    void shouldRejectQueryLongerThanPublishedLimit() {
        AgentRuntimeDefinition.KnowledgeSearchCapability capability = capability();
        capability.setMaxQueryCharacters(4);
        AgentTool tool = factory.create(capability);

        assertThatThrownBy(() -> execute(tool, "{\"query\":\"12345\"}"))
                .hasMessage("knowledge.search查询超过字符上限");
    }

    @Test
    void shouldTruncateStructuredResultsByTokenBudget() {
        AgentRuntimeDefinition.KnowledgeSearchCapability capability = capability();
        capability.setMaxResultTokens(3);
        prepareTokenEstimator();
        when(knowledgeSearchService.searchVersion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new KnowledgeSearchResponse(List.of(result("abcdef"), result("second")), null));

        String output = execute(factory.create(capability), "{\"query\":\"query\"}");

        assertThat(output).contains("\"content\":\"abc\"", "\"truncated\":true");
        assertThat(output).doesNotContain("abcdef", "second");
    }

    @Test
    void shouldReportSaturatedWhenDifferentQueriesReturnNoNewEvidence() {
        prepareTokenEstimator();
        when(knowledgeSearchService.searchVersion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new KnowledgeSearchResponse(List.of(result("同一条知识")), null));

        AgentTool tool = factory.create(capability());
        String first = execute(tool, "{\"query\":\"退款条件\"}");
        assertThat(tool.shouldFinalizeAfterSuccess()).isFalse();
        String second = execute(tool, "{\"query\":\"退款政策\"}");

        assertThat(first).contains("\"status\":\"FOUND\"", "\"newEvidenceCount\":1");
        assertThat(second).contains("\"status\":\"SATURATED\"", "\"newEvidenceCount\":0", "停止调用知识搜索");
        assertThat(tool.shouldFinalizeAfterSuccess()).isTrue();
    }

    @Test
    void shouldAllowOneEmptyQueryRewriteBeforeReportingSaturated() {
        when(knowledgeSearchService.searchVersion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new KnowledgeSearchResponse(List.of(), null));

        AgentTool tool = factory.create(capability());
        String first = execute(tool, "{\"query\":\"不存在的信息\"}");
        String second = execute(tool, "{\"query\":\"换一种说法\"}");

        assertThat(first).contains("\"status\":\"EMPTY\"");
        assertThat(second).contains("\"status\":\"SATURATED\"", "停止调用知识搜索");
        assertThat(tool.shouldFinalizeAfterSuccess()).isTrue();
    }

    private void prepareTokenEstimator() {
        when(tokenCountEstimator.estimate(anyString())).thenAnswer(invocation -> invocation.<String>getArgument(0).length());
    }

    private static String execute(AgentTool tool, String input) {
        return tool.execute(new AgentToolRequest(input,
                AgentToolExecutionContext.builder().executionId("test").userId(12L).build(), null));
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
