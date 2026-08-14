package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;
import java.util.Set;

/** 执行决策校验所需的发布策略和用户确认范围。 */
@Getter
@Builder
public class ExecutionDecisionValidationContext {

    private CapabilityCatalogSnapshot capabilityCatalog;
    private PlanPolicy planPolicy;
    private String supervisorCap;
    private Set<Long> specialistIds;
    private Set<String> confirmedCapabilityCodes;

    public CapabilityCatalogSnapshot capabilityCatalog() {
        return Objects.requireNonNullElseGet(capabilityCatalog, CapabilityCatalogSnapshot::empty);
    }

    public PlanPolicy planPolicy() {
        return Objects.requireNonNull(planPolicy, "主Agent计划策略不能为空");
    }

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }

    public Set<Long> specialistIds() {
        return specialistIds == null ? Set.of() : Set.copyOf(specialistIds);
    }
}
