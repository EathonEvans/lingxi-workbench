package com.lingxi.workbench.admin.evaluation.model.request;

import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationExpectedAction;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    @NotNull(message = "请选择评测场景")
    private EvaluationScenario scenario;

    @NotNull(message = "请选择期望动作")
    private EvaluationExpectedAction expectedAction;

    @Size(max = 10, message = "期望意图编码不能超过10个")
    private List<@NotBlank @Size(max = 100) String> expectedIntentCodes;

    private boolean highRisk;

    @Size(max = 500, message = "用例说明不能超过500字")
    private String note;
}
