package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolApprovalRequest;

/** Tool 外部调用前的 HITL 审批边界。 */
@FunctionalInterface
public interface AgentToolApprovalGate {

    AgentToolApprovalPermit authorize(AgentToolApprovalRequest request);

    static AgentToolApprovalGate noop() {
        return request -> AgentToolApprovalPermit.noop();
    }
}
