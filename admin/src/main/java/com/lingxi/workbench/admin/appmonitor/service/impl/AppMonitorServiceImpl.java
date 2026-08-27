package com.lingxi.workbench.admin.appmonitor.service.impl;

import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.appmonitor.common.enums.AppExecutionMode;
import com.lingxi.workbench.admin.appmonitor.common.enums.AppHealthStatus;
import com.lingxi.workbench.admin.appmonitor.common.enums.AppRunStatus;
import com.lingxi.workbench.admin.appmonitor.common.enums.TraceSourceStatus;
import com.lingxi.workbench.admin.appmonitor.gateway.LangfuseTraceGateway;
import com.lingxi.workbench.admin.appmonitor.gateway.ConversationOpsGateway;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppIssueBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppHealthBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppMonitorSummaryBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppStageMetricBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceEventBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTrendPointBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseObservationBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseTraceResult;
import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import com.lingxi.workbench.admin.appmonitor.model.request.ReplayConversationDlqRequest;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationDlqResultVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppIssueVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppHealthVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppMonitorOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppMonitorSummaryVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppOptionVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppStageMetricVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceDetailVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceEventVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTrendPointVO;
import com.lingxi.workbench.admin.appmonitor.repo.AppMonitorRepo;
import com.lingxi.workbench.admin.appmonitor.service.AppMonitorService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** 应用监控聚合查询实现。 */
@Service
@RequiredArgsConstructor
public class AppMonitorServiceImpl implements AppMonitorService {

    private static final double CRITICAL_SUCCESS_RATE = 90D;
    private static final double DEGRADED_SUCCESS_RATE = 98D;
    private static final long DEGRADED_P95_MS = 15_000L;

    private final AppMonitorRepo monitorRepo;
    private final AiApplicationRepo applicationRepo;
    private final LangfuseTraceGateway langfuseTraceGateway;
    private final ConversationOpsGateway conversationOpsGateway;

    @Override
    public List<AppOptionVO> options() {
        return applicationRepo.findMonitorOptions().stream()
                .map(application -> new AppOptionVO(String.valueOf(application.getId()), application.getName(), application.getCode()))
                .toList();
    }

    @Override
    public AppMonitorOverviewVO overview(AppMonitorQuery query) {
        validateApplication(query.getApplicationId());
        OffsetDateTime from = OffsetDateTime.now().minusHours(query.getRangeHours());
        AppMonitorSummaryBO summary = monitorRepo.summary(query.getApplicationId(), from);
        return AppMonitorOverviewVO.builder()
                .summary(toSummary(summary))
                // 健康矩阵始终保留全部启用应用，applicationId 只筛选总览指标和下方链路。
                .applications(monitorRepo.applicationHealth(from).stream()
                        .map(AppMonitorServiceImpl::toHealth)
                        .toList())
                .stages(monitorRepo.stages(query.getApplicationId(), from).stream().map(AppMonitorServiceImpl::toStage).toList())
                .trend(monitorRepo.trend(query.getApplicationId(), from).stream().map(AppMonitorServiceImpl::toTrend).toList())
                .issues(monitorRepo.issues(query.getApplicationId(), from).stream().map(AppMonitorServiceImpl::toIssue).toList())
                .refreshedAt(OffsetDateTime.now())
                .build();
    }

    private static AppHealthVO toHealth(AppHealthBO health) {
        double successRate = percentage(health.getSuccessRuns(), health.getTotalRuns());
        return AppHealthVO.builder()
                .applicationId(String.valueOf(health.getApplicationId()))
                .applicationName(health.getApplicationName())
                .status(resolveHealthStatus(health.getTotalRuns(), successRate, health.getP95Ms()))
                .totalRuns(health.getTotalRuns())
                .failureRuns(health.getFailureRuns())
                .successRate(successRate)
                .p95Ms(health.getP95Ms())
                .lastRunAt(health.getLastRunAt())
                .build();
    }

    /** 健康阈值是管理端公开 SLO：成功率低于90%为严重，低于98%或P95超过15秒为需关注。 */
    private static AppHealthStatus resolveHealthStatus(long totalRuns, double successRate, long p95Ms) {
        if (totalRuns == 0) {
            return AppHealthStatus.NO_DATA;
        }
        if (successRate < CRITICAL_SUCCESS_RATE) {
            return AppHealthStatus.CRITICAL;
        }
        if (successRate < DEGRADED_SUCCESS_RATE || p95Ms > DEGRADED_P95_MS) {
            return AppHealthStatus.DEGRADED;
        }
        return AppHealthStatus.HEALTHY;
    }

    @Override
    public PageResult<AppTraceVO> traces(AppMonitorQuery query) {
        validateApplication(query.getApplicationId());
        OffsetDateTime from = OffsetDateTime.now().minusHours(query.getRangeHours());
        return monitorRepo.traces(query, from).map(AppMonitorServiceImpl::toTrace);
    }

    @Override
    public AppTraceDetailVO traceDetail(String traceId) {
        AppTraceVO trace = monitorRepo.trace(traceId)
                .map(AppMonitorServiceImpl::toTrace)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Trace不存在"));
        List<AppTraceEventVO> localEvents = monitorRepo.traceEvents(traceId).stream()
                .map(AppMonitorServiceImpl::toEvent)
                .toList();
        OffsetDateTime traceEndedAt = trace.getStartedAt().plus(Duration.ofMillis(trace.getLatencyMs()));
        LangfuseTraceResult langfuse = langfuseTraceGateway.findTrace(
                traceId, trace.getStartedAt().minusMinutes(5), traceEndedAt.plusMinutes(5));
        List<AppTraceEventVO> events = localEvents;
        String source = "LOCAL";
        if (langfuse.status() == TraceSourceStatus.AVAILABLE) {
            events = langfuse.observations().stream().map(AppMonitorServiceImpl::toLangfuseEvent).toList();
            source = "LANGFUSE";
        }
        return AppTraceDetailVO.builder()
                .traceId(traceId)
                .source(source)
                .sourceStatus(langfuse.status())
                .sourceMessage(langfuse.message())
                .summary(trace)
                .events(events)
                .build();
    }

    @Override
    public ConversationOpsOverviewVO conversationOpsOverview() {
        return conversationOpsGateway.overview();
    }

    @Override
    public ConversationDlqResultVO conversationDlq(int limit) {
        return conversationOpsGateway.dlq(limit);
    }

    @Override
    public ConversationReconcileVO reconcileConversations() {
        return conversationOpsGateway.reconcile();
    }

    @Override
    public void replayConversationDlq(ReplayConversationDlqRequest request) {
        conversationOpsGateway.replay(request);
    }

    private void validateApplication(Long applicationId) {
        if (Objects.isNull(applicationId)) {
            return;
        }
        applicationRepo.findById(applicationId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Application不存在"));
    }

    private static AppMonitorSummaryVO toSummary(AppMonitorSummaryBO summary) {
        double successRate = percentage(summary.successRuns(), summary.totalRuns());
        return new AppMonitorSummaryVO(summary.totalRuns(), successRate, summary.avgLatencyMs(), summary.totalTokens());
    }

    private static AppStageMetricVO toStage(AppStageMetricBO stage) {
        double successRate = percentage(stage.total() - stage.errorCount(), stage.total());
        return AppStageMetricVO.builder()
                .stage(stage.stage())
                .name(stageName(stage.stage()))
                .total(stage.total())
                .errorCount(stage.errorCount())
                .successRate(successRate)
                .p95Ms(stage.p95Ms())
                .build();
    }

    private static String stageName(String stage) {
        return switch (stage) {
            case "MESSAGE_PERSIST" -> "消息落库";
            case "CONTEXT_PREPARE" -> "上下文准备";
            case "INTENT_ROUTE" -> "意图路由";
            case "EXECUTION" -> "目标执行";
            case "TERMINAL" -> "结果收口";
            case "MEMORY_POST" -> "记忆后处理";
            default -> stage;
        };
    }

    private static AppTrendPointVO toTrend(AppTrendPointBO point) {
        return new AppTrendPointVO(point.bucket(), point.total(), point.successRuns());
    }

    private static AppIssueVO toIssue(AppIssueBO issue) {
        return new AppIssueVO(issue.errorType(), issue.stage(), issue.count(), issue.latestAt());
    }

    private static AppTraceVO toTrace(AppTraceBO trace) {
        return AppTraceVO.builder()
                .traceId(trace.getTraceId())
                .applicationId(String.valueOf(trace.getApplicationId()))
                .applicationName(trace.getApplicationName())
                .executionMode(AppExecutionMode.fromCode(trace.getExecutionMode()))
                .status(AppRunStatus.fromCode(trace.getStatus()))
                .latencyMs(trace.getLatencyMs())
                .modelCalls(trace.getModelCalls())
                .toolCalls(trace.getToolCalls())
                .totalTokens(trace.getTotalTokens())
                .startedAt(trace.getStartedAt())
                .build();
    }

    private static AppTraceEventVO toEvent(AppTraceEventBO event) {
        return AppTraceEventVO.builder()
                .spanId(event.getSpanId())
                .parentSpanId(event.getParentSpanId())
                .eventType(event.getEventType())
                .stage(event.getStage())
                .status(event.getStatus())
                .latencyMs(Objects.requireNonNullElse(event.getLatencyMs(), 0L))
                .modelCode(event.getModelCode())
                .toolName(event.getToolName())
                .errorCode(event.getErrorCode())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private static AppTraceEventVO toLangfuseEvent(LangfuseObservationBO observation) {
        long latencyMs = 0L;
        if (Objects.nonNull(observation.getEndTime())) {
            latencyMs = Duration.between(observation.getStartTime(), observation.getEndTime()).toMillis();
        }
        return AppTraceEventVO.builder()
                .spanId(observation.getId())
                .parentSpanId(observation.getParentObservationId())
                .eventType(observation.getType())
                .status(resolveObservationStatus(observation.getLevel()))
                .latencyMs(latencyMs)
                .modelCode(observation.getProvidedModelName())
                .toolName(observation.getName())
                .createdAt(observation.getStartTime())
                .build();
    }

    private static String resolveObservationStatus(String level) {
        if ("ERROR".equals(level)) {
            return "ERROR";
        }
        return "SUCCESS";
    }

    private static double percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return BigDecimal.valueOf(numerator * 100D / denominator)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
