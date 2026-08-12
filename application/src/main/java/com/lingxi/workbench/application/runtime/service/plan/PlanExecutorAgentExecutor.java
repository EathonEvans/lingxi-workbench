package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutorRuntimeContext;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将受控规划、步骤执行、重规划和综合作为 PLAN_EXECUTOR Agent 的完整运行循环。 */
@Service
@RequiredArgsConstructor
public class PlanExecutorAgentExecutor implements AgentExecutor {

    private final PlannerRuntimeCoordinator plannerRuntimeCoordinator;

    @Override
    public boolean supports(AgentType agentType) {
        return agentType == AgentType.PLAN_EXECUTOR;
    }

    @Override
    public CapabilityExecutionResult execute(AgentExecutionRequest request) {
        validate(request);
        PlanExecutorRuntimeContext planExecutor = PlanExecutorRuntimeContext.builder()
                .capability(request.getCapability())
                .agent(request.getAgent())
                .routingEvidence(request.getRoutingEvidence())
                .build();
        PlanExecutionResult result = plannerRuntimeCoordinator.execute(PlannerRuntimeRequest.builder()
                .runId(request.getExecutionContext().getExecutionId())
                .conversationContext(request.getExecutionContext().getConversationContext())
                .executionContext(request.getExecutionContext())
                .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                .planExecutor(planExecutor)
                .build());
        return new CapabilityExecutionResult(result.content(), result.modelInstanceId(), result.traces());
    }

    private void validate(AgentExecutionRequest request) {
        AgentRuntimeDefinition definition = request == null || request.getAgent() == null
                ? null
                : request.getAgent().definition();
        if (request == null || request.getCapability() == null || request.getExecutionContext() == null
                || request.getQuery() == null || request.getQuery().isBlank()
                || definition == null || definition.getAgentType() != AgentType.PLAN_EXECUTOR
                || definition.getPlanExecutorPolicy() == null
                || !definition.getPlanExecutorPolicy().isValid()
                || !definition.resolveToolCodes().isEmpty()
                || definition.getKnowledgeSearch() != null
                || definition.getUserPromptVersionId() != null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Plan Executor AgentVersion运行契约不完整");
        }
    }
}
