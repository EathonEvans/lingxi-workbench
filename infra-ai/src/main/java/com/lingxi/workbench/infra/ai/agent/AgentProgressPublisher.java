package com.lingxi.workbench.infra.ai.agent;

import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;

/** 发布不包含模型思维链的 Agent 阶段进度。 */
@FunctionalInterface
public interface AgentProgressPublisher {

    void publish(AgentProgressEvent event);

    static AgentProgressPublisher noop() {
        return event -> {
        };
    }
}
