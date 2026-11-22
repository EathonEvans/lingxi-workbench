package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Admin 后端提交的 Application 草稿意图测试请求。
 *
 * @param applicationId   已校验归属的 Application 主键
 * @param applicationCode Application 稳定编码
 * @param query           本次只读测试问题
 * @param definition      待测试的 Application 草稿定义
 */
public record IntentRoutingPreviewRequest(
        @NotNull @Positive Long applicationId,
        @NotBlank String applicationCode,
        @NotBlank @Size(max = 4000) String query,
        @NotNull ApplicationDefinition definition) {
}
