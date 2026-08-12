package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;

/**
 * 一个 AgentType 对应一种完整的运行循环。
 */
public interface AgentExecutor {

    boolean supports(AgentType agentType);

    CapabilityExecutionResult execute(AgentExecutionRequest request);
}
