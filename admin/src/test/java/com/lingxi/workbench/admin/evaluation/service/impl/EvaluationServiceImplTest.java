package com.lingxi.workbench.admin.evaluation.service.impl;

import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.repo.AppMonitorRepo;
import com.lingxi.workbench.admin.application.gateway.ApplicationIntentPreviewGateway;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.application.repo.AiApplicationVersionRepo;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.admin.evaluation.gateway.LangfuseEvaluationGateway;
import com.lingxi.workbench.admin.evaluation.model.bo.EvaluationTraceContext;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMetricBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMutationResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseOverviewResult;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMutationVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.repo.EvaluationRunRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationServiceImplTest {

    @Test
    void shouldAggregateWeightedOverviewAndPassingRate() {
        LangfuseEvaluationGateway gateway = mock(LangfuseEvaluationGateway.class);
        when(gateway.overview(any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(
                new LangfuseOverviewResult(EvaluationSourceStatus.AVAILABLE, "评测数据已同步", List.of(
                        new LangfuseMetricBO("lingxi.correctness", 4, 3, 4.5),
                        new LangfuseMetricBO("lingxi.relevance", 2, 1, 3.0)), 3));
        EvaluationServiceImpl service = service(gateway, mock(AppMonitorRepo.class));

        EvaluationOverviewVO result = service.overview(new EvaluationOverviewQuery());

        assertThat(result.getTotalScores()).isEqualTo(6);
        assertThat(result.getAverageScore()).isEqualTo(4D);
        assertThat(result.getPassingRate()).isEqualTo(66.67D);
        assertThat(result.getDatasetCount()).isEqualTo(3);
    }

    @Test
    void shouldRejectScoreForUnknownTrace() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        when(monitorRepo.trace("0123456789abcdef")).thenReturn(Optional.empty());
        EvaluationServiceImpl service = service(mock(LangfuseEvaluationGateway.class), monitorRepo);

        assertThatThrownBy(() -> service.createScore(scoreRequest(), 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatusEnum.NOT_FOUND));
    }

    @Test
    void shouldWriteScoreOnlyAfterTraceOwnershipIsVerified() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        LangfuseEvaluationGateway gateway = mock(LangfuseEvaluationGateway.class);
        AppTraceBO trace = new AppTraceBO();
        trace.setTraceId("0123456789abcdef");
        trace.setApplicationId(12L);
        trace.setApplicationName("求职助手");
        when(monitorRepo.trace(trace.getTraceId())).thenReturn(Optional.of(trace));
        when(gateway.createScore(any(CreateEvaluationScoreRequest.class),
                eq(new EvaluationTraceContext(trace.getTraceId(), 12L, "求职助手")), eq(7L)))
                .thenReturn(new LangfuseMutationResult(EvaluationSourceStatus.AVAILABLE, "评分已记录", "score-1"));
        EvaluationServiceImpl service = service(gateway, monitorRepo);

        EvaluationMutationVO result = service.createScore(scoreRequest(), 7L);

        assertThat(result.id()).isEqualTo("score-1");
        assertThat(result.message()).isEqualTo("评分已记录");
    }

    private static CreateEvaluationScoreRequest scoreRequest() {
        CreateEvaluationScoreRequest request = new CreateEvaluationScoreRequest();
        request.setTraceId("0123456789abcdef");
        request.setMetric(EvaluationMetric.CORRECTNESS);
        request.setValue(BigDecimal.valueOf(4));
        request.setComment("结果正确");
        return request;
    }

    private static EvaluationServiceImpl service(
            LangfuseEvaluationGateway gateway, AppMonitorRepo monitorRepo) {
        return new EvaluationServiceImpl(
                gateway,
                monitorRepo,
                mock(EvaluationRunRepo.class),
                mock(AiApplicationRepo.class),
                mock(AiApplicationVersionRepo.class),
                mock(ApplicationIntentPreviewGateway.class),
                Runnable::run);
    }
}
