package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;

/**
 * 将 Agent 执行中的可展示事件发布给对话生成任务。
 *
 * <p>该接口是同步 Workflow 与 SSE 任务之间的显式桥梁，不负责持久化，也不允许
 * 携带工具原始参数或结果。</p>
 */
public interface ChatExecutionEventPublisher {

    /**
     * 发布模型文本增量或 Token 用量。
     */
    void publishText(ChatStreamChunk chunk);

    /**
     * 发布工具执行状态。
     */
    void publishTool(AgentToolExecutionEvent event);

    /**
     * 发布需要用户后续响应的结构化澄清事件。
     */
    default void publishClarification(ChatClarificationEventBO event) {
    }

    /**
     * 返回不观察执行事件的空实现。
     */
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

    /**
     * 创建仅抑制文本增量的发布器。
     *
     * <p>成员 Agent 的回答要作为工具结果回传 Supervisor，不能提前成为最终 Assistant
     * 文本；成员内部工具状态仍保留，便于用户观察真实执行进度。</p>
     */
    static ChatExecutionEventPublisher suppressText(ChatExecutionEventPublisher delegate) {
        ChatExecutionEventPublisher target = java.util.Objects.requireNonNullElseGet(delegate, ChatExecutionEventPublisher::noop);
        return new ChatExecutionEventPublisher() {
            @Override
            public void publishText(ChatStreamChunk chunk) {
            }

            @Override
            public void publishTool(AgentToolExecutionEvent event) {
                target.publishTool(event);
            }
        };
    }
}
