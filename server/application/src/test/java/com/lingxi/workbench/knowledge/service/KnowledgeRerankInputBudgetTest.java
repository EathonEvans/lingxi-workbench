package com.lingxi.workbench.knowledge.service;

import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KnowledgeRerankInputBudgetTest {
    @Test
    void shouldLimitEachDocumentAndChargeRepeatedQueryWithoutChangingSources() {
        KnowledgeSearchResultVO first = result(1L, "abcdefgh");
        KnowledgeRerankInputBudget.Selection selected = budget(4, 10).select("q", List.of(first, result(2L, "second"), result(3L, "third")));
        assertThat(selected.documents()).containsExactly("abcd", "seco");
        assertThat(selected.truncatedDocuments()).isEqualTo(2);
        assertThat(selected.droppedDocuments()).isEqualTo(1);
        assertThat(selected.estimatedTokens()).isEqualTo(10);
        assertThat(first.getContent()).isEqualTo("abcdefgh");
    }

    @Test
    void shouldKeepSurrogatePairIntactWhenCuttingText() {
        assertThat(budget(2, 10).select("q", List.of(result(1L, "a😀b"))).documents()).containsExactly("a");
    }

    @Test
    void shouldRecheckBudgetAfterRepairingSurrogateBoundary() {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setMaxDocumentTokens(2);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(estimator.estimate(anyString())).thenAnswer(call -> {
            String text = call.getArgument(0);
            return text.equals("ab") ? 3 : text.length();
        });
        when(estimator.estimate("ab\uD83D")).thenReturn(2);
        assertThat(new KnowledgeRerankInputBudget(properties, estimator)
                .select("q", List.of(result(1L, "ab😀cd"))).documents()).containsExactly("a");
    }

    @Test
    void shouldRejectQueryThatExhaustsBudgetWithoutCallingProvider() {
        assertThatThrownBy(() -> budget(4, 3).select("long query", List.of(result(1L, "text"))))
                .hasMessageContaining("输入预算不足");
    }

    @Test
    void shouldKeepUnchangedCandidatesInOriginalOrder() {
        KnowledgeRerankInputBudget.Selection selected = budget(10, 50).select("q", List.of(result(2L, "two"), result(1L, "one")));
        assertThat(selected.documents()).containsExactly("two", "one");
        assertThat(selected.truncatedDocuments()).isZero();
        assertThat(selected.droppedDocuments()).isZero();
    }

    private static KnowledgeRerankInputBudget budget(int perDocument, int total) {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setMaxDocumentTokens(perDocument);
        properties.setMaxRerankInputTokens(total);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(estimator.estimate(anyString())).thenAnswer(call -> call.<String>getArgument(0).length());
        return new KnowledgeRerankInputBudget(properties, estimator);
    }

    private static KnowledgeSearchResultVO result(Long id, String content) {
        return KnowledgeSearchResultVO.builder().chunkId(id).content(content).build();
    }
}
