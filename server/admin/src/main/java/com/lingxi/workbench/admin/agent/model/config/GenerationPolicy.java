package com.lingxi.workbench.admin.agent.model.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    public static GenerationPolicy defaults() {
        return GenerationPolicy.builder()
                .temperature(new BigDecimal("0.2"))
                .topP(new BigDecimal("0.9"))
                .build();
    }
}
