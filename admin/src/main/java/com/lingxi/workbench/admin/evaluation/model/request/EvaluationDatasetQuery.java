package com.lingxi.workbench.admin.evaluation.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** 评测数据集分页查询条件。 */
@Getter
@Setter
public class EvaluationDatasetQuery {

    @Min(value = 1, message = "页码不能小于1")
    private int current = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int size = 10;
}
