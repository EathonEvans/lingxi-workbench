package com.lingxi.workbench.admin.appmonitor.repo;

import com.lingxi.workbench.admin.appmonitor.model.bo.AppIssueBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppMonitorSummaryBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppStageMetricBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceEventBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTrendPointBO;
import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import com.lingxi.workbench.admin.appmonitor.repo.mapper.AppMonitorMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 应用监控只读仓库。 */
@Repository
@RequiredArgsConstructor
public class AppMonitorRepo {

    private final AppMonitorMapper mapper;

    public AppMonitorSummaryBO summary(Long applicationId, OffsetDateTime from) {
        return mapper.summary(applicationId, from);
    }

    public List<AppStageMetricBO> stages(Long applicationId, OffsetDateTime from) {
        return mapper.stages(applicationId, from);
    }

    public List<AppTrendPointBO> trend(Long applicationId, OffsetDateTime from) {
        return mapper.trend(applicationId, from);
    }

    public List<AppIssueBO> issues(Long applicationId, OffsetDateTime from) {
        return mapper.issues(applicationId, from);
    }

    public PageResult<AppTraceBO> traces(AppMonitorQuery query, OffsetDateTime from) {
        long offset = (long) (query.getCurrent() - 1) * query.getSize();
        List<AppTraceBO> records = mapper.traces(query, from, offset);
        long total = mapper.traceCount(query, from);
        return new PageResult<>(records, query.getCurrent(), query.getSize(), total);
    }

    public Optional<AppTraceBO> trace(String traceId) {
        return mapper.trace(traceId);
    }

    public List<AppTraceEventBO> traceEvents(String traceId) {
        return mapper.traceEvents(traceId);
    }
}
