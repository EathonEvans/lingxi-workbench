package com.lingxi.workbench.admin.evaluation.model.vo;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 评分游标分页响应。 */
@Getter
@Builder
public class EvaluationScorePageVO {
    private final EvaluationSourceStatus sourceStatus;
    private final String sourceMessage;
    private final List<EvaluationScoreVO> records;
    private final String nextCursor;
}
