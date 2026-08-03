package com.lingxi.workbench.workflow.model;

import java.util.List;

/**
 * Application 级多 Agent 协作定义。
 *
 * <p>Application 决定哪个 Agent 担任 Supervisor、可以委派给哪些成员 Agent，
 * Agent 自身仍只维护独立能力。该定义属于发布快照，不属于任一 Agent。</p>
 *
 * @param enabled    是否启用 Supervisor 协作
 * @param supervisor Supervisor Agent 引用
 * @param members    允许被 Supervisor 委派的成员 Agent 白名单
 * @param limits     单次请求的委派资源边界
 */
public record AgentCollaborationDefinition(
        boolean enabled,
        ApplicationAgentReference supervisor,
        List<ApplicationAgentReference> members,
        AgentCollaborationLimits limits) {

    public AgentCollaborationDefinition {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
