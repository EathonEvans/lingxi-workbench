package com.lingxi.workbench.application.runtime.model;

/**
 * 跨请求恢复时锁定的 capability 与 AgentVersion。
 *
 * @param capabilityCode 能力编码
 * @param agentId        Agent 主键
 * @param agentVersionId 锁定的 Agent 版本主键
 */
public record InteractionResumeTarget(
        String capabilityCode,
        Long agentId,
        Long agentVersionId) {
}
