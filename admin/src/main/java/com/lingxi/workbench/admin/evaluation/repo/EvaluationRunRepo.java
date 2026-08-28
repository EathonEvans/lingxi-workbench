package com.lingxi.workbench.admin.evaluation.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationRunStatus;
import com.lingxi.workbench.admin.evaluation.model.entity.EvaluationRunEntity;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationRunQuery;
import com.lingxi.workbench.admin.evaluation.repo.mapper.EvaluationRunMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.json.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** 评测任务持久化及状态 CAS。 */
@Repository
@RequiredArgsConstructor
public class EvaluationRunRepo {

    private final EvaluationRunMapper mapper;

    public void insert(EvaluationRunEntity entity) {
        mapper.insert(entity);
    }

    public Optional<EvaluationRunEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public PageResult<EvaluationRunEntity> findPage(EvaluationRunQuery query) {
        IPage<EvaluationRunEntity> page = mapper.selectPage(query.toPage(),
                Wrappers.<EvaluationRunEntity>lambdaQuery()
                        .eq(Objects.nonNull(query.getApplicationId()),
                                EvaluationRunEntity::getApplicationId, query.getApplicationId())
                        .eq(Objects.nonNull(query.getApplicationVersionId()),
                                EvaluationRunEntity::getApplicationVersionId, query.getApplicationVersionId())
                        .eq(Objects.nonNull(query.getStatus()),
                                EvaluationRunEntity::getStatus, query.getStatus())
                        .orderByDesc(EvaluationRunEntity::getCreatedAt)
                        .orderByDesc(EvaluationRunEntity::getId));
        return PageResult.convert(page);
    }

    public boolean markRunning(Long id, String executionToken) {
        EvaluationRunEntity update = new EvaluationRunEntity();
        update.setStatus(EvaluationRunStatus.RUNNING);
        update.setExecutionToken(executionToken);
        update.setStartedAt(OffsetDateTime.now());
        update.setErrorMessage(null);
        return mapper.update(update, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.PENDING)) == 1;
    }

    public boolean updateProgress(Long id, String executionToken, int completedCount) {
        EvaluationRunEntity update = new EvaluationRunEntity();
        update.setCompletedCount(completedCount);
        return mapper.update(update, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.RUNNING)
                .eq(EvaluationRunEntity::getExecutionToken, executionToken)) == 1;
    }

    public boolean complete(Long id, String executionToken, int passedCount, int failedCount,
                            boolean productionGatePassed, JSONObject reportSnapshot) {
        EvaluationRunEntity update = new EvaluationRunEntity();
        update.setStatus(EvaluationRunStatus.COMPLETED);
        update.setExecutionToken(null);
        update.setCompletedCount(passedCount + failedCount);
        update.setPassedCount(passedCount);
        update.setFailedCount(failedCount);
        update.setProductionGatePassed(productionGatePassed);
        update.setReportSnapshot(reportSnapshot);
        update.setCompletedAt(OffsetDateTime.now());
        return mapper.update(update, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.RUNNING)
                .eq(EvaluationRunEntity::getExecutionToken, executionToken)
                .set(EvaluationRunEntity::getExecutionToken, null)) == 1;
    }

    public boolean fail(Long id, String executionToken, String errorMessage) {
        EvaluationRunEntity update = new EvaluationRunEntity();
        update.setStatus(EvaluationRunStatus.FAILED);
        update.setExecutionToken(null);
        update.setErrorMessage(errorMessage);
        update.setCompletedAt(OffsetDateTime.now());
        return mapper.update(update, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.RUNNING)
                .eq(EvaluationRunEntity::getExecutionToken, executionToken)
                .set(EvaluationRunEntity::getExecutionToken, null)) == 1;
    }

    public boolean failPending(Long id, String errorMessage) {
        EvaluationRunEntity update = new EvaluationRunEntity();
        update.setStatus(EvaluationRunStatus.FAILED);
        update.setErrorMessage(errorMessage);
        update.setCompletedAt(OffsetDateTime.now());
        return mapper.update(update, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.PENDING)) == 1;
    }

    public boolean cancel(Long id) {
        EvaluationRunEntity update = new EvaluationRunEntity();
        update.setStatus(EvaluationRunStatus.CANCELLED);
        update.setExecutionToken(null);
        update.setCompletedAt(OffsetDateTime.now());
        return mapper.update(update, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .in(EvaluationRunEntity::getStatus,
                        EvaluationRunStatus.PENDING, EvaluationRunStatus.RUNNING)
                .set(EvaluationRunEntity::getExecutionToken, null)) == 1;
    }

    public boolean retry(Long id) {
        return mapper.update(null, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getId, id)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.FAILED)
                .set(EvaluationRunEntity::getStatus, EvaluationRunStatus.PENDING)
                .set(EvaluationRunEntity::getExecutionToken, null)
                .set(EvaluationRunEntity::getCompletedCount, 0)
                .set(EvaluationRunEntity::getPassedCount, 0)
                .set(EvaluationRunEntity::getFailedCount, 0)
                .set(EvaluationRunEntity::getProductionGatePassed, null)
                .set(EvaluationRunEntity::getReportSnapshot, null)
                .set(EvaluationRunEntity::getErrorMessage, null)
                .set(EvaluationRunEntity::getStartedAt, null)
                .set(EvaluationRunEntity::getCompletedAt, null)) == 1;
    }

    public boolean hasPassingRun(Long applicationId, Long applicationVersionId) {
        return mapper.exists(Wrappers.<EvaluationRunEntity>lambdaQuery()
                .eq(EvaluationRunEntity::getApplicationId, applicationId)
                .eq(EvaluationRunEntity::getApplicationVersionId, applicationVersionId)
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.COMPLETED)
                .eq(EvaluationRunEntity::getProductionGatePassed, true));
    }

    public List<Long> findPendingIds(int limit) {
        return mapper.selectList(Wrappers.<EvaluationRunEntity>lambdaQuery()
                        .select(EvaluationRunEntity::getId)
                        .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.PENDING)
                        .orderByAsc(EvaluationRunEntity::getCreatedAt)
                        .last("LIMIT " + limit))
                .stream()
                .map(EvaluationRunEntity::getId)
                .toList();
    }

    public int resetStale(OffsetDateTime cutoff) {
        return mapper.update(null, Wrappers.<EvaluationRunEntity>lambdaUpdate()
                .eq(EvaluationRunEntity::getStatus, EvaluationRunStatus.RUNNING)
                .lt(EvaluationRunEntity::getUpdatedAt, cutoff)
                .set(EvaluationRunEntity::getStatus, EvaluationRunStatus.PENDING)
                .set(EvaluationRunEntity::getExecutionToken, null)
                .set(EvaluationRunEntity::getCompletedCount, 0)
                .set(EvaluationRunEntity::getPassedCount, 0)
                .set(EvaluationRunEntity::getFailedCount, 0)
                .set(EvaluationRunEntity::getProductionGatePassed, null)
                .set(EvaluationRunEntity::getReportSnapshot, null)
                .set(EvaluationRunEntity::getErrorMessage, null)
                .set(EvaluationRunEntity::getStartedAt, null)
                .set(EvaluationRunEntity::getCompletedAt, null));
    }
}
