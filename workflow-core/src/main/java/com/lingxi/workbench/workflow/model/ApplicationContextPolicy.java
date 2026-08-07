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
    private ContextBudgetPolicy budget;

    public ContextualizationPolicy contextualization() {
        return contextualization == null ? ContextualizationPolicy.disabled() : contextualization;
    }

    public ContextBudgetPolicy budget() {
        return budget == null ? ContextBudgetPolicy.defaults() : budget;
    }

    public static ApplicationContextPolicy defaults() {
        return ApplicationContextPolicy.builder()
                .contextualization(ContextualizationPolicy.disabled())
                .budget(ContextBudgetPolicy.defaults())
                .build();
    }
}
