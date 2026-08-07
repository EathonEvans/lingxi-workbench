package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Application 单轮请求的总执行边界。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationRuntimePolicy {

    private int maxModelCalls;
    private int maxToolCalls;
    private int maxDelegations;
    private int totalTokenBudget;
    private int timeoutSeconds;

    public static ApplicationRuntimePolicy defaults() {
        return ApplicationRuntimePolicy.builder()
                .maxModelCalls(16)
                .maxToolCalls(20)
                .maxDelegations(3)
                .totalTokenBudget(64000)
                .timeoutSeconds(300)
                .build();
    }
}
