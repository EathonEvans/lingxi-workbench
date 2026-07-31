package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;

/**
 * 将工具执行状态发布给上层 Agent 运行时。
 */
@FunctionalInterface
public interface AgentToolEventPublisher {

    /**
     * 发布不包含工具敏感参数和结果的执行状态。
     *
     * @param event 工具执行状态事件
     */
    void publish(AgentToolExecutionEvent event);

    /**
     * 返回不处理事件的发布器，供无需观察工具状态的调用链使用。
     */
    static AgentToolEventPublisher noop() {
        return event -> {
        };
    }
}
