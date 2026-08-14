package com.lingxi.workbench.admin.evaluation.model.request;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Langfuse Score 游标分页查询条件。 */
@Getter
@Setter
public class EvaluationScoreQuery {

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int limit = 20;

    @Size(max = 500, message = "分页游标长度不正确")
    private String cursor;

    @Pattern(regexp = "[0-9a-fA-F]{16,64}", message = "Trace ID格式不正确")
    private String traceId;

    private EvaluationMetric metric;
}
