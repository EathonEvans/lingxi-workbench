package com.lingxi.workbench.admin.evaluation.model.vo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** 指定时刻的数据集用例视图。 */
@Getter
@Builder
public class EvaluationCaseListVO {

    private final EvaluationSourceStatus sourceStatus;
    private final String sourceMessage;
    private final String datasetName;
    private final OffsetDateTime datasetVersion;
    private final List<EvaluationCaseVO> records;
}
