package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;

/**
 * 模型生成任务向观察者发布的事件。
 *
 * @param type     事件类型：CHUNK / SNAPSHOT / COMPLETED / FAILED / CANCELLED
 * @param sequence 当前任务内单调递增的增量序号
 * @param content  文本增量或快照内容
 * @param tool     工具状态事件，仅 TOOL 类型非空
 */
public record ChatGenerationEventBO(
        /** 事件类型：CHUNK / SNAPSHOT / COMPLETED / FAILED / CANCELLED。 */
        ChatGenerationEventType type,
        /** 当前任务内单调递增的增量序号。 */
        long sequence,
        /** 文本增量或快照内容。 */
        String content,
        /** 工具状态事件，仅 TOOL 类型非空。 */
        AgentToolExecutionEvent tool) {

    /**
     * 创建文本增量事件。
     */
    public static ChatGenerationEventBO chunk(long sequence, String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.CHUNK, sequence, content, null);
    }

    /**
     * 创建工具执行状态事件。
     */
    public static ChatGenerationEventBO tool(long sequence, AgentToolExecutionEvent tool) {
        return new ChatGenerationEventBO(ChatGenerationEventType.TOOL, sequence, null, tool);
    }

    /**
     * 创建回答快照事件，用于断线重连时恢复已生成内容。
     */
    public static ChatGenerationEventBO snapshot(String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.SNAPSHOT, 0, content, null);
    }

    /**
     * 创建生成完成事件。
     */
    public static ChatGenerationEventBO completed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.COMPLETED, 0, null, null);
    }

    /**
     * 创建生成失败事件。
     */
    public static ChatGenerationEventBO failed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.FAILED, 0, null, null);
    }

    /**
     * 创建用户主动取消生成事件。
     */
    public static ChatGenerationEventBO cancelled() {
        return new ChatGenerationEventBO(ChatGenerationEventType.CANCELLED, 0, null, null);
    }
}
