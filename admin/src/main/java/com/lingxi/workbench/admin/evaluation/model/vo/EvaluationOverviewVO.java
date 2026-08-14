package com.lingxi.workbench.admin.evaluation.model.vo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** 评测中心概览。 */
@Getter
@Builder
public class EvaluationOverviewVO {
    private final EvaluationSourceStatus sourceStatus;
    private final String sourceMessage;
    private final long totalScores;
    private final double averageScore;
    private final double passingRate;
    private final long datasetCount;
    private final List<EvaluationMetricVO> metrics;
    private final OffsetDateTime refreshedAt;
}
