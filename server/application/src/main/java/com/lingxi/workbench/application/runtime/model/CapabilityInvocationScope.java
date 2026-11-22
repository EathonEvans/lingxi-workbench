package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;

/**
 * 单次能力调用相对 Application 根运行的隔离信息。
 *
 * @param agentSessionId AgentScope 隔离会话标识
 * @param executionScope 根运行或内部委派作用域
 * @param eventPublisher 当前调用的事件投影出口
 */
public record CapabilityInvocationScope(String agentSessionId, ExecutionScope executionScope,
                                        ChatExecutionEventPublisher eventPublisher) {

    public CapabilityInvocationScope {
        if (agentSessionId == null || agentSessionId.isBlank() || executionScope == null
                || eventPublisher == null) {
            throw new IllegalArgumentException("能力调用作用域不完整");
        }
    }
}
