package com.lingxi.workbench.admin.evaluation.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.repo.AppMonitorRepo;
import com.lingxi.workbench.admin.application.gateway.ApplicationIntentPreviewGateway;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.application.repo.AiApplicationVersionRepo;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationExpectedAction;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationRunStatus;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.admin.evaluation.gateway.LangfuseEvaluationGateway;
import com.lingxi.workbench.admin.evaluation.model.bo.EvaluationTraceContext;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetItemBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetItemsResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetPageResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMetricBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMutationResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseOverviewResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScoreBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScorePageResult;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationCaseRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationDatasetRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationRunRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationRunQuery;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetPageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationCaseListVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationCaseVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMetricVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMutationVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScorePageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScoreVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationRunDetailVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationRunVO;
import com.lingxi.workbench.admin.evaluation.model.entity.EvaluationRunEntity;
import com.lingxi.workbench.admin.evaluation.repo.EvaluationRunRepo;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.json.JSONArray;
import com.lingxi.workbench.framework.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 评测中心用例实现，业务归属在本地校验，评测事实写入 Langfuse。 */
@Service
@Slf4j
public class EvaluationServiceImpl implements EvaluationService {

    private final LangfuseEvaluationGateway evaluationGateway;
    private final AppMonitorRepo monitorRepo;
    private final EvaluationRunRepo runRepo;
    private final AiApplicationRepo applicationRepo;
    private final AiApplicationVersionRepo applicationVersionRepo;
    private final ApplicationIntentPreviewGateway intentPreviewGateway;
    private final Executor evaluationTaskExecutor;

    public EvaluationServiceImpl(LangfuseEvaluationGateway evaluationGateway, AppMonitorRepo monitorRepo,
                                 EvaluationRunRepo runRepo, AiApplicationRepo applicationRepo,
                                 AiApplicationVersionRepo applicationVersionRepo,
                                 ApplicationIntentPreviewGateway intentPreviewGateway,
                                 @Qualifier("evaluationTaskExecutor") Executor evaluationTaskExecutor) {
        this.evaluationGateway = evaluationGateway;
        this.monitorRepo = monitorRepo;
        this.runRepo = runRepo;
        this.applicationRepo = applicationRepo;
        this.applicationVersionRepo = applicationVersionRepo;
        this.intentPreviewGateway = intentPreviewGateway;
        this.evaluationTaskExecutor = evaluationTaskExecutor;
    }

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
        normalizeExpectedIntentCodes(request);
        EvaluationTraceContext trace = requireTrace(request.getTraceId());
        return requireMutation(evaluationGateway.createCase(datasetName.trim(), request, trace, operatorId));
    }

    @Override
    public EvaluationCaseListVO cases(String datasetName) {
        if (CharSequenceUtil.isBlank(datasetName)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "数据集名称不能为空");
        }
        OffsetDateTime datasetVersion = OffsetDateTime.now(ZoneOffset.UTC);
        LangfuseDatasetItemsResult result = evaluationGateway.datasetItems(datasetName.trim(), datasetVersion);
        return EvaluationCaseListVO.builder()
                .sourceStatus(result.getStatus())
                .sourceMessage(result.getMessage())
                .datasetName(result.getDatasetName())
                .datasetVersion(result.getDatasetVersion())
                .records(result.getItems().stream().map(EvaluationServiceImpl::toCase).toList())
                .build();
    }

    @Override
    public PageResult<EvaluationRunVO> runs(EvaluationRunQuery query) {
        PageResult<EvaluationRunEntity> page = runRepo.findPage(query);
        List<Long> versionIds = page.records().stream()
                .map(EvaluationRunEntity::getApplicationVersionId)
                .distinct()
                .toList();
        Map<Long, AiApplicationVersionEntity> versions = applicationVersionRepo.findAllByIds(versionIds).stream()
                .collect(Collectors.toMap(AiApplicationVersionEntity::getId, Function.identity()));
        return page.map(run -> toRun(run, versions.get(run.getApplicationVersionId())));
    }

    @Override
    public EvaluationRunDetailVO runDetail(Long runId) {
        EvaluationRunEntity run = requireRun(runId);
        AiApplicationVersionEntity version = applicationVersionRepo.findByApplicationIdAndId(
                        run.getApplicationId(), run.getApplicationVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT,
                        "评测任务锁定的ApplicationVersion不存在"));
        return EvaluationRunDetailVO.builder()
                .run(toRun(run, version))
                .datasetSnapshot(run.getDatasetSnapshot())
                .reportSnapshot(run.getReportSnapshot())
                .build();
    }

    @Override
    public EvaluationRunVO createRun(CreateEvaluationRunRequest request, Long operatorId) {
        AiApplicationEntity application = applicationRepo.findById(request.getApplicationId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Application不存在"));
        AiApplicationVersionEntity version = applicationVersionRepo.findByApplicationIdAndId(
                        request.getApplicationId(), request.getApplicationVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "ApplicationVersion不存在"));
        OffsetDateTime datasetVersion = OffsetDateTime.now(ZoneOffset.UTC);
        LangfuseDatasetItemsResult items;
        try {
            items = evaluationGateway.datasetItems(request.getDatasetName().trim(), datasetVersion);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, exception.getMessage());
        }
        requireDatasetItems(items);
        JSONObject dataset = buildDatasetSnapshot(
                request, operatorId, datasetVersion, items.getItems());
        intentPreviewGateway.validateEvaluation(dataset);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setName(request.getName().trim());
        run.setApplicationId(application.getId());
        run.setApplicationVersionId(version.getId());
        run.setDatasetName(request.getDatasetName().trim());
        run.setDatasetVersion(datasetVersion);
        run.setStatus(EvaluationRunStatus.PENDING);
        run.setSampleCount(items.getItems().size());
        run.setCompletedCount(0);
        run.setPassedCount(0);
        run.setFailedCount(0);
        run.setDatasetSnapshot(dataset);
        run.setCreatedBy(operatorId);
        runRepo.insert(run);
        schedule(run.getId());
        return toRun(run, version);
    }

    @Override
    public void cancelRun(Long runId) {
        requireRun(runId);
        if (!runRepo.cancel(runId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "只有等待中或执行中的评测任务可以取消");
        }
    }

    @Override
    public void retryRun(Long runId) {
        requireRun(runId);
        if (!runRepo.retry(runId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "只有失败的评测任务可以重试");
        }
        schedule(runId);
    }

    @Override
    public void requireProductionGatePassed(Long applicationId, Long applicationVersionId) {
        if (!runRepo.hasPassingRun(applicationId, applicationVersionId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "ApplicationVersion尚未通过生产评测门禁");
        }
    }

    private void schedule(Long runId) {
        try {
            evaluationTaskExecutor.execute(() -> executeRun(runId));
        } catch (RuntimeException exception) {
            runRepo.failPending(runId, "评测执行队列已满，请稍后重试");
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "评测执行队列已满，请稍后重试");
        }
    }

    /** 恢复扫描只尝试重新入队；队列繁忙时保留PENDING，等待下一轮扫描。 */
    @Override
    public void recoverRun(Long runId) {
        try {
            evaluationTaskExecutor.execute(() -> executeRun(runId));
        } catch (RuntimeException exception) {
            log.debug("评测恢复队列暂满，等待下一轮扫描，runId={}", runId);
        }
    }

    private void executeRun(Long runId) {
        String executionToken = UUID.randomUUID().toString();
        if (!runRepo.markRunning(runId, executionToken)) {
            return;
        }
        try {
            EvaluationRunEntity run = requireRun(runId);
            JSONArray samples = run.getDatasetSnapshot().getJSONArray("samples");
            List<JSONObject> results = new ArrayList<>(samples.size());
            for (int index = 0; index < samples.size(); index++) {
                JSONObject sample = samples.getJSONObject(index);
                ApplicationIntentPreviewVO preview = intentPreviewGateway.previewVersion(
                        run.getApplicationId(), run.getApplicationVersionId(), sample.getString("query"));
                JSONObject evaluated = new JSONObject();
                evaluated.put("sampleId", sample.getString("id"));
                evaluated.put("result", JSONObject.from(preview));
                results.add(evaluated);
                if (!runRepo.updateProgress(runId, executionToken, index + 1)) {
                    return;
                }
            }
            JSONObject report = intentPreviewGateway.evaluationReport(run.getDatasetSnapshot(), results);
            int sampleCount = report.getIntValue("sampleCount");
            int failedCount = report.getJSONArray("failures").size();
            boolean gatePassed = report.getBooleanValue("productionGatePassed");
            runRepo.complete(runId, executionToken,
                    sampleCount - failedCount, failedCount, gatePassed, report);
        } catch (RuntimeException exception) {
            String message = executionErrorMessage(exception);
            if (runRepo.fail(runId, executionToken, message)) {
                log.warn("评测任务执行失败，runId={}，errorType={}，message={}",
                        runId, exception.getClass().getSimpleName(), message);
            }
        }
    }

    private JSONObject buildDatasetSnapshot(CreateEvaluationRunRequest request, Long operatorId,
                                            OffsetDateTime datasetVersion,
                                            List<LangfuseDatasetItemBO> items) {
        JSONObject dataset = intentPreviewGateway.evaluationTemplate(
                request.getApplicationId(), request.getApplicationVersionId(), datasetVersion.toInstant().toString());
        dataset.put("labelingOwner", String.valueOf(operatorId));
        dataset.put("labeledAt", Instant.now().toString());
        dataset.put("sourceDescription", "Langfuse数据集：" + request.getDatasetName().trim());
        dataset.put("samples", items.stream()
                .map(item -> toEvaluationSample(request.getApplicationId(), item))
                .toList());
        return dataset;
    }

    private static JSONObject toEvaluationSample(Long applicationId, LangfuseDatasetItemBO item) {
        if (Objects.isNull(item.getInput()) || Objects.isNull(item.getExpectedOutput())
                || Objects.isNull(item.getMetadata())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "评测用例缺少输入、期望结果或来源信息，itemId=" + item.getId());
        }
        JSONObject input = item.getInput();
        JSONObject expected = item.getExpectedOutput();
        JSONObject metadata = item.getMetadata();
        if (CharSequenceUtil.isBlank(input.getString("query"))
                || CharSequenceUtil.isBlank(expected.getString("scenario"))
                || CharSequenceUtil.isBlank(expected.getString("expectedAction"))) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "评测用例的输入、场景和期望动作不能为空，itemId=" + item.getId());
        }
        String sourceApplicationId = metadata.getString("applicationId");
        if (!String.valueOf(applicationId).equals(sourceApplicationId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "评测数据集包含其他Application的用例");
        }
        JSONObject sample = new JSONObject();
        sample.put("id", item.getId());
        sample.put("query", input.getString("query"));
        sample.put("scenario", expected.getString("scenario"));
        sample.put("expectedAction", expected.getString("expectedAction"));
        JSONArray expectedCodes = expected.getJSONArray("expectedIntentCodes");
        sample.put("expectedIntentCodes", Objects.isNull(expectedCodes) ? List.of() : expectedCodes);
        sample.put("highRisk", expected.getBooleanValue("highRisk"));
        sample.put("note", metadata.getString("note"));
        return sample;
    }

    private static void requireDatasetItems(LangfuseDatasetItemsResult result) {
        if (result.getStatus() != EvaluationSourceStatus.AVAILABLE) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, result.getMessage());
        }
        if (result.getItems().isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测数据集没有可执行用例");
        }
    }

    private static void normalizeExpectedIntentCodes(CreateEvaluationCaseRequest request) {
        List<String> codes = Objects.requireNonNullElse(
                        request.getExpectedIntentCodes(), List.<String>of()).stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .toList();
        if (codes.size() != new HashSet<>(codes).size()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "期望意图编码不能重复");
        }
        EvaluationExpectedAction action = request.getExpectedAction();
        if (action == EvaluationExpectedAction.EXECUTE_AGENT
                || action == EvaluationExpectedAction.EXECUTE_WORKFLOW
                || action == EvaluationExpectedAction.FIXED_RESPONSE) {
            if (codes.size() != 1) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "可执行动作必须填写一个期望意图编码");
            }
        }
        if (action == EvaluationExpectedAction.CLARIFICATION && codes.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "要求补充信息时至少填写一个期望意图编码");
        }
        request.setExpectedIntentCodes(codes);
    }

    private EvaluationRunEntity requireRun(Long runId) {
        return runRepo.findById(runId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "评测任务不存在"));
    }

    private static EvaluationRunVO toRun(EvaluationRunEntity run, AiApplicationVersionEntity version) {
        return EvaluationRunVO.builder()
                .id(run.getId())
                .name(run.getName())
                .applicationId(run.getApplicationId())
                .applicationName(Objects.isNull(version) ? null : version.getName())
                .applicationVersionId(run.getApplicationVersionId())
                .applicationVersionNo(Objects.isNull(version) ? null : version.getVersionNo())
                .datasetName(run.getDatasetName())
                .datasetVersion(run.getDatasetVersion())
                .status(run.getStatus())
                .statusLabel(run.getStatus().getDescribe())
                .sampleCount(run.getSampleCount())
                .completedCount(run.getCompletedCount())
                .passedCount(run.getPassedCount())
                .failedCount(run.getFailedCount())
                .productionGatePassed(run.getProductionGatePassed())
                .errorMessage(run.getErrorMessage())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .createdAt(run.getCreatedAt())
                .build();
    }

    private static String executionErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "评测执行失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
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

    private static EvaluationCaseVO toCase(LangfuseDatasetItemBO item) {
        JSONObject input = item.getInput();
        JSONObject expected = item.getExpectedOutput();
        JSONObject metadata = item.getMetadata();
        JSONArray expectedCodes = Objects.isNull(expected) ? null : expected.getJSONArray("expectedIntentCodes");
        return EvaluationCaseVO.builder()
                .id(item.getId())
                .traceId(item.getSourceTraceId())
                .input(Objects.isNull(input) ? null : input.getString("query"))
                .scenario(Objects.isNull(expected) ? null : expected.getString("scenario"))
                .expectedAction(Objects.isNull(expected) ? null : expected.getString("expectedAction"))
                .expectedIntentCodes(Objects.isNull(expectedCodes)
                        ? List.of() : expectedCodes.toJavaList(String.class))
                .highRisk(Objects.nonNull(expected) && expected.getBooleanValue("highRisk"))
                .note(Objects.isNull(metadata) ? null : metadata.getString("note"))
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private static double scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
