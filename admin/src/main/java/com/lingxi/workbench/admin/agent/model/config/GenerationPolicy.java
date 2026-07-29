package com.lingxi.workbench.admin.agent.model.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 最终回答生成策略。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GenerationPolicy {
    @NotNull(message = "请设置生成温度")
    @DecimalMin(value = "0.0", message = "生成温度不能小于0")
    @DecimalMax(value = "2.0", message = "生成温度不能大于2")
    private BigDecimal temperature;
    @NotNull(message = "请设置TopP")
    @DecimalMin(value = "0.0", inclusive = false, message = "TopP必须大于0")
    @DecimalMax(value = "1.0", message = "TopP不能大于1")
    private BigDecimal topP;
    @NotNull(message = "请设置回答输出Token上限")
    @Min(value = 16, message = "回答输出Token上限不能小于16")
    @Max(value = 100000, message = "回答输出Token上限不能超过100000")
    private Integer maxOutputTokens;
    @NotNull(message = "请设置回答超时时间")
    @Min(value = 1, message = "回答超时时间不能小于1秒")
    @Max(value = 600, message = "回答超时时间不能超过600秒")
    private Integer timeoutSeconds;
    @NotNull(message = "请设置回答重试次数")
    @Min(value = 0, message = "回答重试次数不能小于0")
    @Max(value = 2, message = "回答重试次数不能超过2")
    private Integer maxRetries;

    public static GenerationPolicy defaults() {
        return GenerationPolicy.builder()
                .temperature(new BigDecimal("0.2"))
                .topP(new BigDecimal("0.9"))
                .maxOutputTokens(2048)
                .timeoutSeconds(60)
                .maxRetries(1)
                .build();
    }
}
