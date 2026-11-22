package com.lingxi.workbench.infra.ai.tool.model;

import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/** Tool 执行前使用的独立安全策略。 */
@Getter
@EqualsAndHashCode
public final class ToolSecurityPolicy {

    private static final int DEFAULT_APPROVAL_TTL_SECONDS = 1800;

    /** 面向审批卡片与审计的风险等级。 */
    private final ToolRiskLevel riskLevel;
    /** 是否必须在执行前取得精确参数审批。 */
    private final boolean approvalRequired;
    /** 审批等待有效期，单位秒。 */
    private final int approvalTtlSeconds;
    /** 面向用户展示的资源作用域说明，不包含敏感参数原文。 */
    private final String resourceScope;

    @Builder
    private ToolSecurityPolicy(ToolRiskLevel riskLevel, boolean approvalRequired, int approvalTtlSeconds,
                               String resourceScope) {
        this.riskLevel = Objects.requireNonNullElse(riskLevel, ToolRiskLevel.LOW);
        this.approvalRequired = approvalRequired;
        this.approvalTtlSeconds = approvalTtlSeconds > 0
                ? approvalTtlSeconds
                : DEFAULT_APPROVAL_TTL_SECONDS;
        this.resourceScope = Objects.requireNonNullElse(resourceScope, "当前会话授权范围");
    }

    public static ToolSecurityPolicy defaults() {
        return ToolSecurityPolicy.builder().build();
    }
}
