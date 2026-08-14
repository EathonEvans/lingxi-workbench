package com.lingxi.workbench.admin.evaluation.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 管理端评分明细。 */
@Getter
@Builder
public class EvaluationScoreVO {
    private final String id;
    private final String metric;
    private final String metricLabel;
    private final double value;
    private final String source;
    private final String traceId;
    private final String comment;
    private final OffsetDateTime timestamp;
}
