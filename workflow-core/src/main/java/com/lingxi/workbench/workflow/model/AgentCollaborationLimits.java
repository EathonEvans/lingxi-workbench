package com.lingxi.workbench.workflow.model;

/**
 * 单次 Supervisor 协作的资源边界。
 *
 * @param maxDelegations 单次用户请求允许的最大 Agent 委派次数
 * @param maxDepth       Agent 调用链最大委派深度；V1 只允许 Supervisor 到成员 Agent，固定为 1
 */
public record AgentCollaborationLimits(int maxDelegations, int maxDepth) {
}
