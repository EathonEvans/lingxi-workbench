package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;

/**
 * 将 Agent 执行中的可展示事件发布给对话生成任务。
 *
 * <p>该接口是同步 Workflow 与 SSE 任务之间的显式桥梁，不负责持久化，也不允许
 * 携带工具原始参数或结果。</p>
 */
public interface ChatExecutionEventPublisher {

    /** 发布模型文本增量或 Token 用量。 */
    void publishText(ChatStreamChunk chunk);

    /** 发布工具执行状态。 */
    void publishTool(AgentToolExecutionEvent event);

    /** 返回不观察执行事件的空实现。 */
    static ChatExecutionEventPublisher noop() {
        return new ChatExecutionEventPublisher() {
            @Override
            public void publishText(ChatStreamChunk chunk) {
            }

            @Override
            public void publishTool(AgentToolExecutionEvent event) {
            }
        };
    }
}
