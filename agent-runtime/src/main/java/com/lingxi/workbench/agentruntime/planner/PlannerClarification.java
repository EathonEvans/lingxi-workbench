package com.lingxi.workbench.agentruntime.planner;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Planner 无法安全决策时请求用户补充的信息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerClarification {

    private String question;
    private List<String> options;
    private PlannerInteractionType interactionType;
    private List<String> capabilityCodes;

    public List<String> options() {
        return options == null ? List.of() : List.copyOf(options);
    }

    public List<String> capabilityCodes() {
        return capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
    }
}
