package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Application 路由、规划和执行共同使用的上下文策略。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationContextPolicy {

    private ContextualizationPolicy contextualization;
    /** 运营侧选择的场景预设，CUSTOM 时使用自定义预算。 */
    private ContextBudgetPreset preset;
    private ContextBudgetPolicy budget;

    public ContextualizationPolicy contextualization() {
        return contextualization == null ? ContextualizationPolicy.disabled() : contextualization;
    }

    public ContextBudgetPolicy budget() {
        return budget == null ? preset().toBudget() : budget;
    }

    public ContextBudgetPreset preset() {
        return preset == null ? ContextBudgetPreset.BALANCED : preset;
    }

    public static ApplicationContextPolicy defaults() {
        return ApplicationContextPolicy.builder()
                .contextualization(ContextualizationPolicy.disabled())
                .preset(ContextBudgetPreset.BALANCED)
                .budget(ContextBudgetPolicy.defaults())
                .build();
    }
}
