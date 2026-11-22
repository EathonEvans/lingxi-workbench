package com.lingxi.workbench.admin.evaluation.model.request;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationRunStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 评测任务分页筛选。 */
@Getter
@Setter
public class EvaluationRunQuery extends PageQuery {

    private Long applicationId;
    private Long applicationVersionId;
    private EvaluationRunStatus status;
}
