package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 新增或修改模型实例请求。 */
public record SaveModelInstanceRequest(
        @NotNull(message = "请选择供应商") @Positive Long providerId,
        @NotNull(message = "请选择 API 密钥") @Positive Long credentialId,
        @NotBlank(message = "请输入实例名称") @Size(max = 64) String name,
        @NotBlank(message = "请输入实例编码")
                @Pattern(
                        regexp = "^[a-z][a-z0-9_-]{1,63}$",
                        message = "实例编码仅支持小写字母、数字、中划线和下划线")
                String code,
        @NotBlank(message = "请输入供应商模型标识") @Size(max = 128) String modelCode,
        @NotNull(message = "请选择模型类型") ModelType modelType,
        List<@Size(max = 64) String> capabilities,
        @DecimalMin("0") @DecimalMax("2") BigDecimal temperature,
        @DecimalMin("0") @DecimalMax("1") BigDecimal topP,
        @Positive Integer maxTokens,
        @Positive Integer contextWindow,
        List<@Size(max = 128) String> stopSequences,
        @NotNull @Min(1) @Max(600) Integer timeoutSeconds,
        @NotNull @Min(0) @Max(2) Integer maxRetries,
        @NotNull(message = "请选择模型状态") EnableStatus status) {}
