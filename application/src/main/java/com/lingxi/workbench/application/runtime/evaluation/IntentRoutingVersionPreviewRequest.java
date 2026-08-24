package com.lingxi.workbench.application.runtime.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 不可变 ApplicationVersion 的只读意图测试请求。
 *
 * @param applicationId        Application主键
 * @param applicationVersionId 不可变版本主键
 * @param query                评测问题
 */
public record IntentRoutingVersionPreviewRequest(
        @NotNull @Positive Long applicationId,
        @NotNull @Positive Long applicationVersionId,
        @NotBlank @Size(max = 4000) String query) {
}
