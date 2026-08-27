package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 提供给 Supervisor 的最小发布能力描述，不包含任何资源主键。 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CapabilityToolDescriptor {

    private String code;
    private ApplicationCapabilityType type;
    private String name;
    private String description;
    private JSONObject inputSchema;
    private JSONObject outputSchema;
    private CapabilityRiskLevel riskLevel;
}
