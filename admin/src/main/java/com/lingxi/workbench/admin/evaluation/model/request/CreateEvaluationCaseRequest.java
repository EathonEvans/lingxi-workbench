package com.lingxi.workbench.admin.evaluation.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 将真实运行沉淀为可重复执行的评测用例。 */
@Getter
@Setter
public class CreateEvaluationCaseRequest {

    @NotBlank(message = "请填写Trace ID")
    @Pattern(regexp = "[0-9a-fA-F]{16,64}", message = "Trace ID格式不正确")
    private String traceId;

    @NotBlank(message = "请填写用例输入")
    @Size(max = 8000, message = "用例输入不能超过8000字")
    private String input;

    @NotBlank(message = "请填写期望结果")
    @Size(max = 8000, message = "期望结果不能超过8000字")
    private String expectedOutput;

    @Size(max = 500, message = "用例说明不能超过500字")
    private String note;
}
