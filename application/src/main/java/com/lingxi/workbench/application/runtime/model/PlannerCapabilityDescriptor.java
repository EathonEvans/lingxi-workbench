package com.lingxi.workbench.application.runtime.model;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 提供给Planner的最小能力描述，不包含任何资源主键。 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerCapabilityDescriptor {

    private String code;
    private ApplicationCapabilityType type;
    private String name;
    private String description;
    private JSONObject inputSchema;
    private JSONObject outputSchema;
    private CapabilityRiskLevel riskLevel;
}
