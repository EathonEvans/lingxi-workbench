package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ToolApprovalAuthorization;
import com.lingxi.workbench.infra.ai.tool.AgentToolApprovalGate;
import com.lingxi.workbench.infra.ai.tool.AgentToolApprovalPermit;
import com.lingxi.workbench.infra.ai.tool.HumanInteractionRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 为一次 Agent 执行创建带持久化回调的 HITL 门禁。 */
@Component
@RequiredArgsConstructor
public class ToolApprovalGateFactory {

    private final ToolApprovalTxService txService;

    public AgentToolApprovalGate create(ChatExecutionContext execution, AgentRuntimeSnapshot agent) {
        return request -> {
            ToolApprovalAuthorization authorization = txService.authorize(request, execution, agent);
            if (!authorization.permitted()) {
                throw new HumanInteractionRequiredException(Long.valueOf(
                        authorization.interaction().getInteractionId()));
            }
            if (authorization.invocationId() == null) {
                return AgentToolApprovalPermit.noop();
            }
            return new AgentToolApprovalPermit() {
                @Override
                public void complete(String result) {
                    txService.complete(authorization.invocationId(), result);
                }

                @Override
                public void fail(RuntimeException exception) {
                    txService.fail(authorization.invocationId(), exception);
                }
            };
        };
    }
}
