package com.lingxi.workbench.admin.evaluation.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.repo.AppMonitorRepo;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.admin.evaluation.gateway.LangfuseEvaluationGateway;
import com.lingxi.workbench.admin.evaluation.model.bo.EvaluationTraceContext;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetPageResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMetricBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMutationResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseOverviewResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScoreBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScorePageResult;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationCaseRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationDatasetRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetPageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMetricVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMutationVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScorePageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScoreVO;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/** 评测中心用例实现，业务归属在本地校验，评测事实写入 Langfuse。 */
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final LangfuseEvaluationGateway evaluationGateway;
    private final AppMonitorRepo monitorRepo;

    @Override
    public EvaluationOverviewVO overview(EvaluationOverviewQuery query) {
        OffsetDateTime to = OffsetDateTime.now();
        LangfuseOverviewResult result = evaluationGateway.overview(to.minusHours(query.getRangeHours()), to);
        long total = result.metrics().stream().mapToLong(LangfuseMetricBO::total).sum();
        long passing = result.metrics().stream().mapToLong(LangfuseMetricBO::passing).sum();
        double weightedTotal = result.metrics().stream()
                .mapToDouble(metric -> metric.averageValue() * metric.total())
                .sum();
        return EvaluationOverviewVO.builder()
                .sourceStatus(result.status())
                .sourceMessage(result.message())
                .totalScores(total)
                .averageScore(total == 0 ? 0D : scale(weightedTotal / total))
                .passingRate(total == 0 ? 0D : scale(passing * 100D / total))
                .datasetCount(result.datasetCount())
                .metrics(result.metrics().stream().map(EvaluationServiceImpl::toMetric).toList())
                .refreshedAt(to)
                .build();
    }

    @Override
    public EvaluationScorePageVO scores(EvaluationScoreQuery query) {
        LangfuseScorePageResult result = evaluationGateway.scores(query);
        return EvaluationScorePageVO.builder()
                .sourceStatus(result.status())
                .sourceMessage(result.message())
                .records(result.scores().stream().map(EvaluationServiceImpl::toScore).toList())
                .nextCursor(result.nextCursor())
                .build();
    }

    @Override
    public EvaluationDatasetPageVO datasets(EvaluationDatasetQuery query) {
        LangfuseDatasetPageResult result = evaluationGateway.datasets(query);
        return EvaluationDatasetPageVO.builder()
                .sourceStatus(result.status())
                .sourceMessage(result.message())
                .records(result.datasets().records().stream().map(EvaluationServiceImpl::toDataset).toList())
                .current(result.datasets().current())
                .size(result.datasets().size())
                .total(result.datasets().total())
                .build();
    }

    @Override
    public EvaluationMutationVO createScore(CreateEvaluationScoreRequest request, Long operatorId) {
        EvaluationTraceContext trace = requireTrace(request.getTraceId());
        return requireMutation(evaluationGateway.createScore(request, trace, operatorId));
    }

    @Override
    public EvaluationMutationVO createDataset(CreateEvaluationDatasetRequest request, Long operatorId) {
        return requireMutation(evaluationGateway.createDataset(request, operatorId));
    }

    @Override
    public EvaluationMutationVO createCase(String datasetName, CreateEvaluationCaseRequest request, Long operatorId) {
        if (CharSequenceUtil.isBlank(datasetName)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "数据集名称不能为空");
        }
        EvaluationTraceContext trace = requireTrace(request.getTraceId());
        return requireMutation(evaluationGateway.createCase(datasetName.trim(), request, trace, operatorId));
    }

    private EvaluationTraceContext requireTrace(String traceId) {
        AppTraceBO trace = monitorRepo.trace(traceId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "运行链路不存在，无法评测"));
        return new EvaluationTraceContext(trace.getTraceId(), trace.getApplicationId(), trace.getApplicationName());
    }

    private static EvaluationMutationVO requireMutation(LangfuseMutationResult result) {
        if (result.status() != EvaluationSourceStatus.AVAILABLE || CharSequenceUtil.isBlank(result.id())) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, result.message());
        }
        return new EvaluationMutationVO(result.id(), result.message());
    }

    private static EvaluationMetricVO toMetric(LangfuseMetricBO metric) {
        EvaluationMetric type = EvaluationMetric.fromScoreName(metric.name()).orElseThrow();
        return new EvaluationMetricVO(type.name(), type.getLabel(), metric.total(), scale(metric.averageValue()));
    }

    private static EvaluationScoreVO toScore(LangfuseScoreBO score) {
        EvaluationMetric metric = EvaluationMetric.fromScoreName(score.getName()).orElseThrow();
        return EvaluationScoreVO.builder()
                .id(score.getId())
                .metric(metric.name())
                .metricLabel(metric.getLabel())
                .value(scale(score.getValue()))
                .source(score.getSource())
                .traceId(score.getTraceId())
                .comment(score.getComment())
                .timestamp(score.getTimestamp())
                .build();
    }

    private static EvaluationDatasetVO toDataset(LangfuseDatasetBO dataset) {
        return EvaluationDatasetVO.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .build();
    }

    private static double scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
