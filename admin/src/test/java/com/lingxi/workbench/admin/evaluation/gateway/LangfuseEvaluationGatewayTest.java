package com.lingxi.workbench.admin.evaluation.gateway;

import com.lingxi.workbench.admin.appmonitor.config.LangfuseProperties;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetPageResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMetricBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScorePageResult;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangfuseEvaluationGatewayTest {

    @Test
    void shouldExposeDisabledStateWithoutCallingExternalApi() {
        LangfuseEvaluationGateway gateway = new LangfuseEvaluationGateway(new LangfuseProperties());

        LangfuseScorePageResult result = gateway.scores(new EvaluationScoreQuery());

        assertThat(result.status()).isEqualTo(EvaluationSourceStatus.DISABLED);
        assertThat(result.scores()).isEmpty();
    }

    @Test
    void shouldParseAndMergeNumericScoreMetrics() {
        List<LangfuseMetricBO> totals = LangfuseEvaluationGateway.parseMetrics("""
                {"data":[
                  {"name":"lingxi.correctness","count_count":5,"avg_value":4.2},
                  {"name":"external.metric","count_count":9,"avg_value":1.0}
                ]}
                """);
        List<LangfuseMetricBO> passing = LangfuseEvaluationGateway.parseMetrics("""
                {"data":[{"name":"lingxi.correctness","count_count":4}]}
                """);

        List<LangfuseMetricBO> result = LangfuseEvaluationGateway.mergeMetrics(totals, passing);

        assertThat(result).singleElement().satisfies(metric -> {
            assertThat(metric.name()).isEqualTo("lingxi.correctness");
            assertThat(metric.total()).isEqualTo(5);
            assertThat(metric.passing()).isEqualTo(4);
            assertThat(metric.averageValue()).isEqualTo(4.2D);
        });
    }

    @Test
    void shouldParseOnlyManagedNumericScores() {
        LangfuseScorePageResult result = LangfuseEvaluationGateway.parseScores("""
                {
                  "data":[
                    {
                      "id":"score-1","name":"lingxi.relevance","value":4.5,"dataType":"NUMERIC",
                      "source":"API","timestamp":"2026-08-13T02:00:00Z","comment":"回答切题",
                      "subject":{"kind":"trace","id":"0123456789abcdef"}
                    },
                    {"id":"score-2","name":"other","value":1,"dataType":"NUMERIC"},
                    {"id":"score-3","name":"lingxi.safety","value":true,"dataType":"BOOLEAN"}
                  ],
                  "meta":{"limit":20,"cursor":"next-page"}
                }
                """);

        assertThat(result.scores()).singleElement().satisfies(score -> {
            assertThat(score.getName()).isEqualTo("lingxi.relevance");
            assertThat(score.getTraceId()).isEqualTo("0123456789abcdef");
            assertThat(score.getComment()).isEqualTo("回答切题");
        });
        assertThat(result.nextCursor()).isEqualTo("next-page");
    }

    @Test
    void shouldParseDatasetPageMetadata() {
        EvaluationDatasetQuery query = new EvaluationDatasetQuery();
        query.setCurrent(2);
        query.setSize(10);

        LangfuseDatasetPageResult result = LangfuseEvaluationGateway.parseDatasets("""
                {
                  "data":[{
                    "id":"dataset-1","name":"回归基线","description":"发布前固定用例",
                    "createdAt":"2026-08-12T02:00:00Z","updatedAt":"2026-08-13T02:00:00Z"
                  }],
                  "meta":{"page":2,"limit":10,"totalItems":21,"totalPages":3}
                }
                """, query);

        assertThat(result.datasets().records()).singleElement().satisfies(dataset ->
                assertThat(dataset.getName()).isEqualTo("回归基线"));
        assertThat(result.datasets().current()).isEqualTo(2);
        assertThat(result.datasets().total()).isEqualTo(21);
    }

    @Test
    void shouldUseStableScoreIdPerTraceAndMetric() {
        String correctnessId = LangfuseEvaluationGateway.scoreId(
                "0123456789abcdef", EvaluationMetric.CORRECTNESS);

        assertThat(LangfuseEvaluationGateway.scoreId(
                "0123456789abcdef", EvaluationMetric.CORRECTNESS)).isEqualTo(correctnessId);
        assertThat(LangfuseEvaluationGateway.scoreId(
                "0123456789abcdef", EvaluationMetric.SAFETY)).isNotEqualTo(correctnessId);
    }
}
