package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.CapabilityDefinition;

/**
 * Application fallback 角色在当前发布快照中解析出的执行目标。
 *
 * @param capability 能力目录中 fallback 对应的能力定义
 * @param agent      能力目录锁定的 Agent 运行快照
 */
public record FallbackTarget(
        CapabilityDefinition capability,
        AgentRuntimeSnapshot agent) {
}
