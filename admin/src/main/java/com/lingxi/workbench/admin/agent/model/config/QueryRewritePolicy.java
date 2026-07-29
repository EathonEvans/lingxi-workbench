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

/** 用户问题改写策略。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryRewritePolicy {
    @NotNull(message = "请设置是否启用问题改写")
    private Boolean enabled;
    @NotNull(message = "请设置改写历史轮数")
    @Min(value = 0, message = "改写历史轮数不能小于0")
    @Max(value = 50, message = "改写历史轮数不能超过50")
    private Integer maxHistoryRounds;
    @NotNull(message = "请设置改写历史Token上限")
    @Min(value = 0, message = "改写历史Token上限不能小于0")
    @Max(value = 50000, message = "改写历史Token上限不能超过50000")
    private Integer maxHistoryTokens;
    @NotNull(message = "请设置改写温度")
    @DecimalMin(value = "0.0", message = "改写温度不能小于0")
    @DecimalMax(value = "2.0", message = "改写温度不能大于2")
    private BigDecimal temperature;
    @NotNull(message = "请设置改写输出Token上限")
    @Min(value = 16, message = "改写输出Token上限不能小于16")
    @Max(value = 2048, message = "改写输出Token上限不能超过2048")
    private Integer maxOutputTokens;
    @NotNull(message = "请设置改写输出字符上限")
    @Min(value = 32, message = "改写输出字符上限不能小于32")
    @Max(value = 10000, message = "改写输出字符上限不能超过10000")
    private Integer maxOutputCharacters;
    @NotNull(message = "请设置问题改写超时时间")
    @Min(value = 500, message = "问题改写超时时间不能小于500毫秒")
    @Max(value = 60000, message = "问题改写超时时间不能超过60000毫秒")
    private Integer timeoutMillis;
    @NotNull(message = "请设置改写失败是否回退原问题")
    private Boolean fallbackToOriginal;

    /** V1 默认关闭，待改写运行节点接入后由应用显式开启。 */
    public static QueryRewritePolicy defaults() {
        return QueryRewritePolicy.builder()
                .enabled(false)
                .maxHistoryRounds(6)
                .maxHistoryTokens(2000)
                .temperature(new BigDecimal("0.1"))
                .maxOutputTokens(128)
                .maxOutputCharacters(500)
                .timeoutMillis(3000)
                .fallbackToOriginal(true)
                .build();
    }
}
