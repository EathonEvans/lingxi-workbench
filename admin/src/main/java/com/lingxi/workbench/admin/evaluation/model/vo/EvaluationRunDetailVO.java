package com.lingxi.workbench.admin.evaluation.model.vo;

import com.lingxi.workbench.framework.json.JSONObject;
import lombok.Builder;
import lombok.Getter;

/** 评测任务详情，报告为 Application 生成的版本化协议。 */
@Getter
@Builder
public class EvaluationRunDetailVO {

    private final EvaluationRunVO run;
    private final JSONObject datasetSnapshot;
    private final JSONObject reportSnapshot;
}
