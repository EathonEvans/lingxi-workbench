package com.lingxi.workbench.admin.evaluation.model.bo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** 指定时刻的数据集用例快照。 */
@Getter
@Builder
public class LangfuseDatasetItemsResult {

    private final EvaluationSourceStatus status;
    private final String message;
    private final String datasetName;
    private final OffsetDateTime datasetVersion;
    private final List<LangfuseDatasetItemBO> items;
}
