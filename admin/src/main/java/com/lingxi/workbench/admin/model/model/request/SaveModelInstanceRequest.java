package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
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
        /** 所属供应商主键。 */
        @NotNull(message = "请选择供应商")
        @Positive
        Long providerId,

        /** 关联 API 密钥主键。 */
        @NotNull(message = "请选择 API 密钥")
        @Positive
        Long credentialId,

        /** 实例名称。 */
        @NotBlank(message = "请输入实例名称")
        @Size(max = 64)
        String name,

        /** 实例编码，全局唯一，仅支持小写字母、数字、中划线和下划线。 */
        @NotBlank(message = "请输入实例编码")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "实例编码仅支持小写字母、数字、中划线和下划线")
        String code,

        /** 供应商模型标识，如 DeepSeek/chat-v3。 */
        @NotBlank(message = "请输入供应商模型标识")
        @Size(max = 128)
        String modelCode,

        /** 模型类型：CHAT / EMBEDDING / RERANK。 */
        @NotNull(message = "请选择模型类型")
        ModelType modelType,

        /** 模型支持的可选能力列表。 */
        List<ModelCapability> capabilities,

        /** 温度参数，控制生成随机性，范围 0~2。 */
        @DecimalMin("0") @DecimalMax("2")
        BigDecimal temperature,
        /** 核采样参数，控制生成多样性，范围 0~1。 */
        @DecimalMin("0")
        @DecimalMax("1")
        BigDecimal topP,
        /** 单次生成最大 Token 数。 */
        @Positive
        Integer maxTokens,
        /** 模型上下文窗口大小。 */
        @Positive
        Integer contextWindow,

        /** 停止生成序列列表。 */
        List<@Size(max = 128) String> stopSequences,
        /** 请求超时时间（秒），范围 1~600。 */
        @NotNull
        @Min(1)
        @Max(600)
        Integer timeoutSeconds,

        /** 最大重试次数，范围 0~2。 */
        @NotNull
        @Min(0)
        @Max(2)
        Integer maxRetries,
        /** 启用状态：ACTIVE / DISABLED。 */
        @NotNull(message = "请选择模型状态")
        EnableStatus status
) {}
