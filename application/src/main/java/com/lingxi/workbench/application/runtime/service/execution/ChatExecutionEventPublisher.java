package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;

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

    /** 发布可向用户展示并持久化恢复的执行时间线。 */
    default void publishExecution(ChatExecutionTimelineEventBO event) {
    }

    /** 发布计划步骤的安全文本增量，不将其混入最终Assistant回答。 */
    default void publishStepOutput(ChatStepOutputEventBO event) {
    }

    /** 当前Assistant生成任务是否已被用户取消或进入其他终态。 */
    default boolean isCancelled() {
        return false;
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

            @Override
            public void publishExecution(ChatExecutionTimelineEventBO event) {
                target.publishExecution(event);
            }

            @Override
            public void publishStepOutput(ChatStepOutputEventBO event) {
                target.publishStepOutput(event);
            }

            @Override
            public boolean isCancelled() {
                return target.isCancelled();
            }
        };
    }

    /**
     * 创建计划步骤专属发布器。
     *
     * <p>步骤模型文本进入独立的步骤输出事件；工具、执行时间线和取消状态继续复用
     * 根任务通道，避免中间步骤回答污染最终Assistant正文。</p>
     */
    static ChatExecutionEventPublisher forPlanStep(ChatExecutionEventPublisher delegate, String runId,
                                                   Integer planNo, String stepId) {
        ChatExecutionEventPublisher target = java.util.Objects.requireNonNullElseGet(
                delegate, ChatExecutionEventPublisher::noop);
        return new ChatExecutionEventPublisher() {
            @Override
            public void publishText(ChatStreamChunk chunk) {
                target.publishStepOutput(new ChatStepOutputEventBO(runId, planNo, stepId, chunk.content()));
            }

            @Override
            public void publishTool(AgentToolExecutionEvent event) {
                target.publishTool(event);
            }

            @Override
            public void publishClarification(ChatClarificationEventBO event) {
                target.publishClarification(event);
            }

            @Override
            public void publishExecution(ChatExecutionTimelineEventBO event) {
                target.publishExecution(event);
            }

            @Override
            public void publishStepOutput(ChatStepOutputEventBO event) {
                target.publishStepOutput(event);
            }

            @Override
            public boolean isCancelled() {
                return target.isCancelled();
            }
        };
    }
}
