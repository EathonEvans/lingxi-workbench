package com.lingxi.workbench.infra.ai.agent.model;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentProgressPhase;

/**
 * Agent 可向用户展示的阶段进度，不包含模型思维链、工具参数或工具结果。
 *
 * @param phase              当前安全阶段
 * @param message            面向用户的简短状态
 * @param roundNo            当前模型轮次
 * @param toolCallCount      当前已计入预算的工具调用次数
 */
public record AgentProgressEvent(AgentProgressPhase phase, String message, int roundNo, int toolCallCount) {
}
