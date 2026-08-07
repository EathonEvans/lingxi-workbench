package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Application 规划能力的发布策略；M3 只冻结配置，M4 才启用执行。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerPolicy {

    private boolean enabled;
    private Long chatModelInstanceId;
    private Long promptVersionId;
    private int maxSteps;
    private int maxReplans;
    private int maxModelCalls;
    private int maxToolCalls;
    private int tokenBudget;
    private int timeoutSeconds;

    public static PlannerPolicy disabled() {
        return PlannerPolicy.builder()
                .enabled(false)
                .maxSteps(8)
                .maxReplans(1)
                .maxModelCalls(12)
                .maxToolCalls(20)
                .tokenBudget(32000)
                .timeoutSeconds(300)
                .build();
    }
}
