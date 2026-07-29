package com.lingxi.workbench.admin.model.model.request;

import com.alibaba.fastjson2.JSONObject;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增或修改模型实例请求。
 *
 * @param providerId     所属供应商主键
 * @param credentialId   关联 API 密钥主键
 * @param name           实例名称
 * @param code           实例编码，全局唯一，仅支持小写字母、数字、中划线和下划线
 * @param modelCode      供应商模型标识，如 DeepSeek/chat-v3
 * @param modelType      模型类型：CHAT / EMBEDDING / RERANK
 * @param capabilities   模型支持的可选能力列表
 * @param temperature    温度参数，控制生成随机性，范围 0~2
 * @param topP           核采样参数，控制生成多样性，范围 0~1
 * @param maxTokens      单次生成最大 Token 数
 * @param contextWindow  模型上下文窗口大小
 * @param stopSequences  停止生成序列列表
 * @param extensionParameters 供应商特有的 OpenAI Chat 请求体扩展参数
 * @param timeoutSeconds 请求超时时间（秒），范围 1~600
 * @param maxRetries     最大重试次数，范围 0~2
 * @param status         启用状态：ACTIVE / DISABLED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SaveModelInstanceRequest {

    @NotNull(message = "请选择供应商")
    @Positive
    private Long providerId;

    @NotNull(message = "请选择 API 密钥")
    @Positive
    private Long credentialId;

    @NotBlank(message = "请输入实例名称")
    @Size(max = 64)
    private String name;

    @NotBlank(message = "请输入实例编码")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "实例编码仅支持小写字母、数字、中划线和下划线")
    private String code;

    @NotBlank(message = "请输入供应商模型标识")
    @Size(max = 128)
    private String modelCode;

    @NotNull(message = "请选择模型类型")
    private ModelType modelType;

    private List<ModelCapability> capabilities;

    @DecimalMin("0")
    @DecimalMax("2")
    private BigDecimal temperature;

    @DecimalMin("0")
    @DecimalMax("1")
    private BigDecimal topP;

    @Positive
    private Integer maxTokens;

    @Positive
    private Integer contextWindow;

    private List<@Size(max = 128) String> stopSequences;

    private JSONObject extensionParameters;

    @NotNull
    @Min(1)
    @Max(600)
    private Integer timeoutSeconds;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer maxRetries;

    @NotNull(message = "请选择模型状态")
    private EnableStatus status;
}
