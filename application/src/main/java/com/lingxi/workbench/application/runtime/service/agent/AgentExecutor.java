package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;

/**
 * 一个 AgentRole 对应一种完整的运行循环。
 */
public interface AgentExecutor {

    boolean supports(AgentRole role);

    CapabilityExecutionResult execute(AgentExecutionRequest request);
}
