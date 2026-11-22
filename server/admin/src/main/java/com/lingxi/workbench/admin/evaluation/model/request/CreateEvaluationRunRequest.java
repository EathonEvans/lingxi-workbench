package com.lingxi.workbench.admin.evaluation.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 创建一次绑定正式版本和数据集快照的意图路由评测。 */
@Getter
@Setter
public class CreateEvaluationRunRequest {

    @NotBlank(message = "请填写评测任务名称")
    @Size(max = 120, message = "评测任务名称不能超过120字")
    private String name;

    @NotNull(message = "请选择Application")
    private Long applicationId;

    @NotNull(message = "请选择ApplicationVersion")
    private Long applicationVersionId;

    @NotBlank(message = "请选择评测数据集")
    @Size(max = 100, message = "数据集名称不能超过100字")
    private String datasetName;
}
