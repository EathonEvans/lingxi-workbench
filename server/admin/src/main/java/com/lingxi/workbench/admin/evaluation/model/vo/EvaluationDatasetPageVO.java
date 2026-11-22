package com.lingxi.workbench.admin.evaluation.model.vo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 数据集分页响应。 */
@Getter
@Builder
public class EvaluationDatasetPageVO {
    private final EvaluationSourceStatus sourceStatus;
    private final String sourceMessage;
    private final List<EvaluationDatasetVO> records;
    private final int current;
    private final int size;
    private final long total;
}
