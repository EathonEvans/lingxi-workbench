package com.lingxi.workbench.application.runtime.model.request;

import com.lingxi.workbench.application.runtime.common.enums.InteractionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** 用户对 HITL 交互作出的带版本决策。 */
@Data
public class InteractionDecisionRequest {

    @NotNull(message = "请选择审批结果")
    private InteractionDecision decision;

    @NotNull(message = "交互版本不能为空")
    @PositiveOrZero(message = "交互版本不能小于零")
    private Integer expectedRevision;

    @NotBlank(message = "审批指纹不能为空")
    private String fingerprint;
}
