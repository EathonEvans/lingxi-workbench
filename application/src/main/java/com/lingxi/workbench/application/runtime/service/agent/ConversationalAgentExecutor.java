package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.service.agent.AgentChatService;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 复用现有模型与 Tool 循环执行 REACT、SUPERVISOR Agent。
 */
@Service
@RequiredArgsConstructor
public class ConversationalAgentExecutor implements AgentExecutor {

    private final AgentChatService agentChatService;

    @Override
    public boolean supports(AgentType agentType) {
        return agentType == AgentType.REACT || agentType == AgentType.SUPERVISOR;
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
