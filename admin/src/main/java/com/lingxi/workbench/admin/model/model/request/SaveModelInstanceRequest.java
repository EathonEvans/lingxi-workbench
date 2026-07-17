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
        /**
         * 供应商id
         */
        @NotNull(message = "请选择供应商")
        @Positive
        Long providerId,

        /**
         * ApiKeyId
         */
        @NotNull(message = "请选择 API 密钥")
        @Positive
        Long credentialId,

        /**
         * 模型实例名称
         */
        @NotBlank(message = "请输入实例名称")
        @Size(max = 64)
        String name,

        /**
         * 自定义模型编码
         */
        @NotBlank(message = "请输入实例编码")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "实例编码仅支持小写字母、数字、中划线和下划线")
        String code,

        /**
         * 模型标识，比如DeespSeek/chat-v3， 具体看供应商和你要使用的模型
         */
        @NotBlank(message = "请输入供应商模型标识")
        @Size(max = 128)
        String modelCode,

        /**
         * 模型类型， 对话模型， 向量化模型， 重排序
         */
        @NotNull(message = "请选择模型类型")
        ModelType modelType,

        /**
         * 模型支持的可选能力
         */
        List<ModelCapability> capabilities,

        /**
         * 模型参数- 温度
         */
        @DecimalMin("0") @DecimalMax("2")
        BigDecimal temperature,
        /**
         * 同温度差不多的意思
         */
        @DecimalMin("0")
        @DecimalMax("1")
        BigDecimal topP,
        /**
         * 支持最大token
         */
        @Positive
        Integer maxTokens,
        /**
         * 内容窗口数
         */
        @Positive
        Integer contextWindow,

        List<@Size(max = 128) String> stopSequences,
        /**
         * 请求超时时间
         */
        @NotNull
        @Min(1)
        @Max(600)
        Integer timeoutSeconds,

        /**
         * 最大重试次数
         */
        @NotNull
        @Min(0)
        @Max(2)
        Integer maxRetries,
        /**
         * 状态
         */
        @NotNull(message = "请选择模型状态")
        EnableStatus status
) {}
