package com.lingxi.workbench.admin.appmonitor.service.impl;

import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppMonitorSummaryBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppHealthBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceEventBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseTraceResult;
import com.lingxi.workbench.admin.appmonitor.common.enums.AppHealthStatus;
import com.lingxi.workbench.admin.appmonitor.common.enums.TraceSourceStatus;
import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppMonitorOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceDetailVO;
import com.lingxi.workbench.admin.appmonitor.repo.AppMonitorRepo;
import com.lingxi.workbench.admin.appmonitor.gateway.LangfuseTraceGateway;
import com.lingxi.workbench.admin.appmonitor.gateway.ConversationOpsGateway;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppMonitorServiceImplTest {

    @Test
    void shouldCalculateOverviewFromTerminalRuns() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        when(monitorRepo.summary(isNull(), any(OffsetDateTime.class)))
                .thenReturn(new AppMonitorSummaryBO(8, 6, 1250, 3200));
        when(monitorRepo.applicationHealth(any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.stages(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.trend(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.issues(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        AppMonitorServiceImpl service = new AppMonitorServiceImpl(
                monitorRepo, applicationRepo, mock(LangfuseTraceGateway.class),
                mock(ConversationOpsGateway.class));

        AppMonitorOverviewVO result = service.overview(new AppMonitorQuery());

        assertThat(result.getSummary().totalRuns()).isEqualTo(8);
        assertThat(result.getSummary().successRate()).isEqualTo(75D);
        assertThat(result.getSummary().avgLatencyMs()).isEqualTo(1250);
        assertThat(result.getSummary().totalTokens()).isEqualTo(3200);
    }

    @Test
    void shouldClassifyApplicationHealthUsingPublishedThresholds() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        when(monitorRepo.summary(isNull(), any(OffsetDateTime.class)))
                .thenReturn(new AppMonitorSummaryBO(20, 17, 1250, 3200));
        when(monitorRepo.applicationHealth(any(OffsetDateTime.class))).thenReturn(List.of(
                health(1L, "健康应用", 100, 99, 1, 2000),
                health(2L, "慢应用", 100, 99, 1, 16000),
                health(3L, "异常应用", 10, 8, 2, 2000),
                health(4L, "新应用", 0, 0, 0, 0)));
        when(monitorRepo.stages(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.trend(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.issues(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        AppMonitorServiceImpl service = new AppMonitorServiceImpl(
                monitorRepo, applicationRepo, mock(LangfuseTraceGateway.class),
                mock(ConversationOpsGateway.class));

        AppMonitorOverviewVO result = service.overview(new AppMonitorQuery());

        assertThat(result.getApplications()).extracting(item -> item.getStatus()).containsExactly(
                AppHealthStatus.HEALTHY,
                AppHealthStatus.DEGRADED,
                AppHealthStatus.CRITICAL,
                AppHealthStatus.NO_DATA);
    }

    @Test
    void shouldRejectUnknownApplicationFilter() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        when(applicationRepo.findById(99L)).thenReturn(Optional.empty());
        AppMonitorServiceImpl service = new AppMonitorServiceImpl(
                monitorRepo, applicationRepo, mock(LangfuseTraceGateway.class),
                mock(ConversationOpsGateway.class));
        AppMonitorQuery query = new AppMonitorQuery();
        query.setApplicationId(99L);

        assertThatThrownBy(() -> service.overview(query))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatusEnum.NOT_FOUND));
    }

    @Test
    void shouldExposeLocalModelLatencyAndTokenMetrics() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        LangfuseTraceGateway langfuseTraceGateway = mock(LangfuseTraceGateway.class);
        AppTraceBO trace = trace("trace-1");
        AppTraceEventBO modelEvent = modelEvent();
        when(monitorRepo.trace("trace-1")).thenReturn(Optional.of(trace));
        when(monitorRepo.traceEvents("trace-1")).thenReturn(List.of(modelEvent));
        when(langfuseTraceGateway.findTrace(any(), any(), any())).thenReturn(
                new LangfuseTraceResult(TraceSourceStatus.DISABLED, "完整链路查询未启用", List.of()));
        AppMonitorServiceImpl service = new AppMonitorServiceImpl(
                monitorRepo, applicationRepo, langfuseTraceGateway, mock(ConversationOpsGateway.class));

        AppTraceDetailVO result = service.traceDetail("trace-1");

        assertThat(result.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getFirstResponseMs()).isEqualTo(730L);
            assertThat(event.getModelInstanceId()).isEqualTo(9L);
            assertThat(event.getModelInstanceCode()).isEqualTo("default-chat");
            assertThat(event.getModelCode()).isEqualTo("deepseek-v4-flash");
            assertThat(event.getModelProviderName()).isEqualTo("DeepSeek");
            assertThat(event.getModelCallNo()).isEqualTo(2);
            assertThat(event.getPromptTokens()).isEqualTo(1200);
            assertThat(event.getCompletionTokens()).isEqualTo(240);
        });
    }

    private static AppHealthBO health(Long id, String name, long total, long success, long failures, long p95Ms) {
        AppHealthBO health = new AppHealthBO();
        health.setApplicationId(id);
        health.setApplicationName(name);
        health.setTotalRuns(total);
        health.setSuccessRuns(success);
        health.setFailureRuns(failures);
        health.setP95Ms(p95Ms);
        health.setLastRunAt(OffsetDateTime.now());
        return health;
    }

    private static AppTraceBO trace(String traceId) {
        AppTraceBO trace = new AppTraceBO();
        trace.setTraceId(traceId);
        trace.setApplicationId(1L);
        trace.setApplicationName("求职助手");
        trace.setExecutionMode("PLAN_EXECUTE");
        trace.setStatus("SUCCESS");
        trace.setLatencyMs(2000L);
        trace.setModelCalls(1L);
        trace.setToolCalls(0L);
        trace.setTotalTokens(1440L);
        trace.setStartedAt(OffsetDateTime.now());
        return trace;
    }

    private static AppTraceEventBO modelEvent() {
        AppTraceEventBO event = new AppTraceEventBO();
        event.setSpanId("span-1");
        event.setEventType("RUNTIME_PHASE");
        event.setStage("EXECUTION");
        event.setStatus("SUCCESS");
        event.setLatencyMs(1000L);
        event.setModelInstanceId(9L);
        event.setModelInstanceCode("default-chat");
        event.setModelCode("deepseek-v4-flash");
        event.setModelProviderName("DeepSeek");
        event.setModelCallNo(2);
        event.setFirstResponseMs(730L);
        event.setPromptTokens(1200);
        event.setCompletionTokens(240);
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }
}
