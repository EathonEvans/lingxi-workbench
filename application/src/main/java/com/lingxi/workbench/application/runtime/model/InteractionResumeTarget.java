package com.lingxi.workbench.application.runtime.model;

/** 跨请求恢复时锁定的 capability 与 AgentVersion。 */
public record InteractionResumeTarget(
        String capabilityCode,
        Long agentId,
        Long agentVersionId) {
}
