package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.SupervisorRuntimeContext;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Supervisor 的完整运行循环：规划、委派、重规划和结果综合。 */
@Service
@RequiredArgsConstructor
public class SupervisorAgentExecutor implements AgentExecutor {

    private final PlannerRuntimeCoordinator plannerRuntimeCoordinator;

    @Override
    public boolean supports(AgentRole role) {
        return role == AgentRole.SUPERVISOR;
    }

    @Override
    public CapabilityExecutionResult execute(AgentExecutionRequest request) {
        validate(request);
        SupervisorRuntimeContext supervisor = SupervisorRuntimeContext.builder()
                .capability(request.getCapability())
                .agent(request.getAgent())
                .routingEvidence(request.getRoutingEvidence())
                .build();
        PlanExecutionResult result = plannerRuntimeCoordinator.execute(PlannerRuntimeRequest.builder()
                .runId(request.getExecutionContext().getExecutionId())
                .conversationContext(request.getExecutionContext().getConversationContext())
                .executionContext(request.getExecutionContext())
                .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                .supervisor(supervisor)
                .build());
        return new CapabilityExecutionResult(result.content(), result.modelInstanceId(), result.traces());
    }

    private void validate(AgentExecutionRequest request) {
        AgentRuntimeDefinition definition = request == null || request.getAgent() == null
                ? null
                : request.getAgent().definition();
        if (request == null || request.getCapability() == null || request.getExecutionContext() == null
                || request.getQuery() == null || request.getQuery().isBlank()
                || definition == null || definition.getRole() != AgentRole.SUPERVISOR
                || definition.getPlanPolicy() == null
                || !definition.resolveToolCodes().isEmpty()
                || definition.getKnowledgeSearch() != null
                || definition.getUserPromptVersionId() != null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Supervisor AgentVersion运行契约不完整");
        }
    }
}
