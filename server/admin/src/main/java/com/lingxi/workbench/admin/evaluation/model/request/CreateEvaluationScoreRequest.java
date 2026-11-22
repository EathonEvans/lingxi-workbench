package com.lingxi.workbench.admin.evaluation.model.request;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 对单次 Application 运行提交人工评分。 */
@Getter
@Setter
public class CreateEvaluationScoreRequest {

    @NotBlank(message = "请填写Trace ID")
    @Pattern(regexp = "[0-9a-fA-F]{16,64}", message = "Trace ID格式不正确")
    private String traceId;

    @NotNull(message = "请选择评测维度")
    private EvaluationMetric metric;

    @NotNull(message = "请填写评分")
    @DecimalMin(value = "1", message = "评分不能低于1分")
    @DecimalMax(value = "5", message = "评分不能高于5分")
    private BigDecimal value;

    @Size(max = 500, message = "评语不能超过500字")
    private String comment;
}
