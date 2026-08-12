package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;

/** Application fallback 角色在当前发布快照中解析出的执行目标。 */
public record FallbackTarget(
        ApplicationCapabilityDefinition capability,
        AgentRuntimeSnapshot agent) {
}
