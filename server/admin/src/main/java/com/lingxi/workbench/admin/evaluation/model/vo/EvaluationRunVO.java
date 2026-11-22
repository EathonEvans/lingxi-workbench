package com.lingxi.workbench.admin.evaluation.model.vo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationRunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 评测任务列表项。 */
@Getter
@Builder
public class EvaluationRunVO {

    private final Long id;
    private final String name;
    private final Long applicationId;
    private final String applicationName;
    private final Long applicationVersionId;
    private final Integer applicationVersionNo;
    private final String datasetName;
    private final OffsetDateTime datasetVersion;
    private final EvaluationRunStatus status;
    private final String statusLabel;
    private final Integer sampleCount;
    private final Integer completedCount;
    private final Integer passedCount;
    private final Integer failedCount;
    private final Boolean productionGatePassed;
    private final String errorMessage;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime completedAt;
    private final OffsetDateTime createdAt;
}
