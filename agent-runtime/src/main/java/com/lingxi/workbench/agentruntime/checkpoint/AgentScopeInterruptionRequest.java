package com.lingxi.workbench.agentruntime.checkpoint;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;

/**
 * @param checkpoint AgentScope 已持久化前可复原的状态快照
 * @param definition 待确认 Tool 的冻结定义
 * @param executionContext 服务端可信执行上下文
 */
public record AgentScopeInterruptionRequest(AgentScopeCheckpoint checkpoint,
                                            AgentToolDefinition definition,
                                            AgentToolExecutionContext executionContext) {
}
