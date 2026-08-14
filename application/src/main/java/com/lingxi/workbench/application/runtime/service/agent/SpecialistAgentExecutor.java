package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 领域专家通过 ReAct 和自身业务工具完成具体任务。
 */
@Service
@RequiredArgsConstructor
public class SpecialistAgentExecutor implements AgentExecutor {

    private final AgentChatService agentChatService;

    @Override
    public boolean supports(AgentRole role) {
        return role == AgentRole.SPECIALIST;
    }

    @Override
    public CapabilityExecutionResult execute(AgentExecutionRequest request) {
        String answer = agentChatService.chat(request.getAgent(), request.getQuery(), request.getExecutionContext());
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent能力必须返回非空结果");
        }
        return new CapabilityExecutionResult(
                answer,
                request.getAgent().definition().getChatModelInstanceId(),
                List.of());
    }
}
