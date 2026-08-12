package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Agent 运行时澄清或风险确认的持久化上下文。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InteractionSessionContext {

    private PlannerClarification clarification;
    private List<String> capabilityCodes;
    private String originalQuery;
    private String response;

    public List<String> capabilityCodes() {
        return capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
    }
}
