package com.lingxi.workbench.agentruntime.checkpoint;

/**
 * @param invocationId 数据库 CAS 已领取的调用意图主键
 * @param idempotencyKey 外部 Tool 必须透传并用于结果查询的稳定幂等键
 * @param toolCall 允许执行的 AgentScope Tool 调用
 */
public record AgentScopeApprovedInvocation(Long invocationId, String idempotencyKey,
                                           AgentScopeCheckpoint.ToolCallSnapshot toolCall) {
}
