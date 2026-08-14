package com.lingxi.workbench.admin.evaluation.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** 评测概览统计范围，默认查看最近七天。 */
@Getter
@Setter
public class EvaluationOverviewQuery {

    @Min(value = 1, message = "统计范围不能少于1小时")
    @Max(value = 720, message = "统计范围不能超过30天")
    private int rangeHours = 168;
}
