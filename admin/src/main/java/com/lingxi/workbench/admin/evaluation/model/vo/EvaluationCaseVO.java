package com.lingxi.workbench.admin.evaluation.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** 回归数据集中的人工标注用例。 */
@Getter
@Builder
public class EvaluationCaseVO {

    private final String id;
    private final String traceId;
    private final String input;
    private final String scenario;
    private final String expectedAction;
    private final List<String> expectedIntentCodes;
    private final boolean highRisk;
    private final String note;
    private final OffsetDateTime updatedAt;
}
