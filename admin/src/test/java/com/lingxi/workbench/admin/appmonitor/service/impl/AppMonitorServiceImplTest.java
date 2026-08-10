package com.lingxi.workbench.admin.appmonitor.service.impl;

import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppMonitorSummaryBO;
import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppMonitorOverviewVO;
import com.lingxi.workbench.admin.appmonitor.repo.AppMonitorRepo;
import com.lingxi.workbench.admin.appmonitor.gateway.LangfuseTraceGateway;
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
        when(monitorRepo.stages(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.trend(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(monitorRepo.issues(isNull(), any(OffsetDateTime.class))).thenReturn(List.of());
        AppMonitorServiceImpl service = new AppMonitorServiceImpl(
                monitorRepo, applicationRepo, mock(LangfuseTraceGateway.class));

        AppMonitorOverviewVO result = service.overview(new AppMonitorQuery());

        assertThat(result.getSummary().totalRuns()).isEqualTo(8);
        assertThat(result.getSummary().successRate()).isEqualTo(75D);
        assertThat(result.getSummary().avgLatencyMs()).isEqualTo(1250);
        assertThat(result.getSummary().totalTokens()).isEqualTo(3200);
    }

    @Test
    void shouldRejectUnknownApplicationFilter() {
        AppMonitorRepo monitorRepo = mock(AppMonitorRepo.class);
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        when(applicationRepo.findById(99L)).thenReturn(Optional.empty());
        AppMonitorServiceImpl service = new AppMonitorServiceImpl(
                monitorRepo, applicationRepo, mock(LangfuseTraceGateway.class));
        AppMonitorQuery query = new AppMonitorQuery();
        query.setApplicationId(99L);

        assertThatThrownBy(() -> service.overview(query))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatusEnum.NOT_FOUND));
    }
}
