package com.lingxi.workbench.workflow.model;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Application 上下文预算预设。
 *
 * <p>预设表达运营人员的使用场景，发布时仍会冻结完整的
 * {@link ContextBudgetPolicy}，避免运行时依赖可变默认值。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ContextBudgetPreset {

    ECONOMY("ECONOMY", "经济"),
    BALANCED("BALANCED", "均衡"),
    DEEP("DEEP", "深度"),
    CUSTOM("CUSTOM", "自定义");

    @JSONField(value = true)
    private final String code;

    private final String describe;

    /** 根据场景预设生成可冻结的完整 Token 分区。 */
    public ContextBudgetPolicy toBudget() {
        return switch (this) {
            case ECONOMY -> ContextBudgetPolicy.builder()
                    .maxHistoryRounds(8)
                    .maxSystemPromptTokens(2_000)
                    .maxCapabilityTokens(2_000)
                    .maxHistoryTokens(6_000)
                    .maxMemoryTokens(1_000)
                    .maxInputTokens(2_000)
                    .maxObservationTokens(6_000)
                    .reservedOutputTokens(2_048)
                    .build();
            case BALANCED, CUSTOM -> ContextBudgetPolicy.defaults();
            case DEEP -> ContextBudgetPolicy.builder()
                    .maxHistoryRounds(40)
                    .maxSystemPromptTokens(4_000)
                    .maxCapabilityTokens(6_000)
                    .maxHistoryTokens(18_000)
                    .maxMemoryTokens(6_000)
                    .maxInputTokens(4_000)
                    .maxObservationTokens(18_000)
                    .reservedOutputTokens(8_000)
                    .build();
        };
    }

    @JSONCreator
    public static ContextBudgetPreset fromCode(String code) {
        for (ContextBudgetPreset preset : values()) {
            if (preset.code.equals(code)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("不支持的上下文预算预设：" + code);
    }
}
