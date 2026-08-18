package com.lingxi.workbench.agentruntime.checkpoint;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;

/**
 * @param checkpoint Runtime生成的完整检查点
 * @param definition 待批准Tool的冻结定义
 * @param executionContext Tool可信执行上下文
 */
public record HumanApprovalRequest(RuntimeCheckpoint checkpoint, AgentToolDefinition definition,
                                   AgentToolExecutionContext executionContext) {
}
