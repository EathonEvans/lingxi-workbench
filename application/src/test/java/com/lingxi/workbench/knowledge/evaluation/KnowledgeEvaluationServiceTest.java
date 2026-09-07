package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchDiagnostics;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchHit;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchResponse;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeEvaluationServiceTest {
    @Test
    void shouldCompareVectorTopKWithFinalRankingFromSameRecall() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.searchVersion(any())).thenReturn(response(List.of(10L, 20L, 30L), List.of(20L, 30L)));
        KnowledgeEvaluationReport report = service(search).evaluate(dataset(sample("positive", Set.of(20L, 30L), false)));
        assertThat(report.isPassed()).isTrue();
        assertThat(report.getVectorMetrics()).isEqualTo(new KnowledgeEvaluationMetrics(0.5, 0.5));
        assertThat(report.getFinalMetrics()).isEqualTo(new KnowledgeEvaluationMetrics(1.0, 1.0));
        assertThat(JSON.toJSONString(report)).doesNotContain("private query");
        verify(search, times(1)).searchVersion(any());
    }

    @Test
    void permissionFailureOnlyPassesWhenExplicitlyExpected() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.searchVersion(any())).thenThrow(new BusinessException(HttpStatusEnum.FORBIDDEN, "private details"));
        KnowledgeEvaluationReport expected = service(search).evaluate(dataset(sample("deny", Set.of(), true)));
        assertThat(expected.isPassed()).isTrue();
        assertThat(expected.getFinalMetrics()).isNull();
        assertThat(expected.getSamples().getFirst().getErrorCode()).isEqualTo(403);
        KnowledgeEvaluationReport unexpected = service(search).evaluate(dataset(sample("positive", Set.of(10L), false)));
        assertThat(unexpected.isPassed()).isFalse();
        assertThat(unexpected.getFinalMetrics().recallAtK()).isZero();
        assertThat(JSON.toJSONString(unexpected)).doesNotContain("private details");
    }

    @Test
    void missingEvidenceAndProviderFailureMustNotBecomeSuccess() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.searchVersion(any())).thenReturn(response(List.of(), List.of()))
                .thenThrow(new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "private provider body"));
        KnowledgeEvaluationReport report = service(search).evaluate(new KnowledgeEvaluationDataset("v1",
                List.of(sample("no-evidence", Set.of(10L), false), sample("failed", Set.of(20L), false))));
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getSamples()).extracting(KnowledgeEvaluatedSample::isPassed).containsExactly(false, false);
        assertThat(report.getSamples().get(1).getErrorCode()).isEqualTo(503);
        assertThat(report.getFinalMetrics().recallAtK()).isZero();
    }

    @Test
    void negativeQueryMustActuallyHaveNoResults() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.searchVersion(any())).thenReturn(response(List.of(), List.of()));
        assertThat(service(search).evaluate(dataset(sample("empty", Set.of(), false))).isPassed()).isTrue();
        when(search.searchVersion(any())).thenReturn(response(List.of(10L), List.of(10L)));
        assertThat(service(search).evaluate(dataset(sample("empty", Set.of(), false))).isPassed()).isFalse();
    }

    @Test
    void invalidOrDuplicateSamplesMustFailBeforeAnyQuery() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        KnowledgeEvaluationSample invalid = new KnowledgeEvaluationSample("invalid", request().toBuilder().topK(0).build(), Set.of(10L), false);
        assertThatThrownBy(() -> service(search).evaluate(dataset(invalid))).hasMessageContaining("不符合约束");
        KnowledgeEvaluationSample sample = sample("same", Set.of(10L), false);
        assertThatThrownBy(() -> service(search).evaluate(new KnowledgeEvaluationDataset("v1", List.of(sample, sample))))
                .hasMessageContaining("不能重复");
        verifyNoInteractions(search);
    }

    private static KnowledgeEvaluationService service(KnowledgeSearchService search) {
        return new KnowledgeEvaluationService(search, Validation.buildDefaultValidatorFactory().getValidator());
    }

    static KnowledgeSearchRequest request() {
        return KnowledgeSearchRequest.builder().query("private query").userId(12L).knowledgeBaseId(1L).indexVersionId(81L)
                .embeddingModelInstanceId(7L).topK(2).similarityThreshold(0.5).timeoutSeconds(10).build();
    }

    static KnowledgeEvaluationSample sample(String id, Set<Long> expected, boolean denied) {
        return new KnowledgeEvaluationSample(id, request(), expected, denied);
    }

    static KnowledgeEvaluationDataset dataset(KnowledgeEvaluationSample sample) {
        return new KnowledgeEvaluationDataset("v1", List.of(sample));
    }

    private static KnowledgeSearchResponse response(List<Long> candidates, List<Long> selected) {
        return new KnowledgeSearchResponse(List.of(), KnowledgeSearchDiagnostics.builder().stageMillis(Map.of())
                .candidates(candidates.stream().map(id -> new KnowledgeSearchHit(id, 0.7, null)).toList())
                .selected(selected.stream().map(id -> new KnowledgeSearchHit(id, 0.7, 0.8)).toList()).build());
    }
}
