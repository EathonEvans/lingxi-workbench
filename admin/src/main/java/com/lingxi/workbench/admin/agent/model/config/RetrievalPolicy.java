package com.lingxi.workbench.admin.agent.model.config;

import com.lingxi.workbench.admin.agent.common.enums.EmptyResultStrategy;
import com.lingxi.workbench.admin.agent.common.enums.RetrievalFailureStrategy;
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

/** 向量召回策略。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RetrievalPolicy {
    @NotNull(message = "请设置召回条数")
    @Min(value = 1, message = "召回条数不能小于1")
    @Max(value = 100, message = "召回条数不能超过100")
    private Integer topK;
    @NotNull(message = "请设置相似度阈值")
    @DecimalMin(value = "0.0", message = "相似度阈值不能小于0")
    @DecimalMax(value = "1.0", message = "相似度阈值不能大于1")
    private BigDecimal similarityThreshold;
    @NotNull(message = "请设置检索上下文Token上限")
    @Min(value = 128, message = "检索上下文Token上限不能小于128")
    @Max(value = 100000, message = "检索上下文Token上限不能超过100000")
    private Integer maxContextTokens;
    @NotNull(message = "请选择空召回处理策略")
    private EmptyResultStrategy emptyResultStrategy;
    @NotNull(message = "请选择检索异常处理策略")
    private RetrievalFailureStrategy failureStrategy;

    public static RetrievalPolicy defaults() {
        return RetrievalPolicy.builder()
                .topK(5)
                .similarityThreshold(new BigDecimal("0.65"))
                .maxContextTokens(4000)
                .emptyResultStrategy(EmptyResultStrategy.RETURN_EMPTY_HINT)
                .failureStrategy(RetrievalFailureStrategy.FAIL_REQUEST)
                .build();
    }
}
