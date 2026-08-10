package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import lombok.EqualsAndHashCode;

/** 模型生成任务向观察者发布的安全事件。 */
@EqualsAndHashCode
public final class ChatGenerationEventBO {

    private final ChatGenerationEventType type;
    private final long sequence;
    private final String content;
    private final AgentToolExecutionEvent tool;
    private final ChatClarificationEventBO clarification;
    private final ChatExecutionTimelineEventBO execution;
    private final ChatErrorBO error;

    private ChatGenerationEventBO(
            ChatGenerationEventType type,
            long sequence,
            String content,
            AgentToolExecutionEvent tool,
            ChatClarificationEventBO clarification,
            ChatExecutionTimelineEventBO execution,
            ChatErrorBO error) {
        this.type = type;
        this.sequence = sequence;
        this.content = content;
        this.tool = tool;
        this.clarification = clarification;
        this.execution = execution;
        this.error = error;
    }

    public static ChatGenerationEventBO chunk(long sequence, String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.CHUNK, sequence, content, null, null, null, null);
    }

    public static ChatGenerationEventBO tool(long sequence, AgentToolExecutionEvent tool) {
        return new ChatGenerationEventBO(ChatGenerationEventType.TOOL, sequence, null, tool, null, null, null);
    }

    public static ChatGenerationEventBO clarification(long sequence, ChatClarificationEventBO clarification) {
        return new ChatGenerationEventBO(ChatGenerationEventType.CLARIFICATION, sequence, null, null, clarification, null, null);
    }

    public static ChatGenerationEventBO execution(long sequence, ChatGenerationEventType type,
                                                  ChatExecutionTimelineEventBO execution) {
        if (type != ChatGenerationEventType.PLANNING && type != ChatGenerationEventType.PLAN
                && type != ChatGenerationEventType.STEP && type != ChatGenerationEventType.REPLANNING) {
            throw new IllegalArgumentException("执行时间线事件类型不正确");
        }
        return new ChatGenerationEventBO(type, sequence, null, null, null, execution, null);
    }

    public static ChatGenerationEventBO snapshot(String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.SNAPSHOT, 0, content, null, null, null, null);
    }

    public static ChatGenerationEventBO completed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.COMPLETED, 0, null, null, null, null, null);
    }

    public static ChatGenerationEventBO failed() {
        return failed("AI_MODEL_ERROR", "模型服务暂时不可用，请稍后重试");
    }

    public static ChatGenerationEventBO failed(String code, String message) {
        return new ChatGenerationEventBO(ChatGenerationEventType.FAILED, 0, null, null, null, null, new ChatErrorBO(code, message));
    }

    public static ChatGenerationEventBO cancelled() {
        return new ChatGenerationEventBO(ChatGenerationEventType.CANCELLED, 0, null, null, null, null, null);
    }

    public ChatGenerationEventType type() {
        return type;
    }

    public long sequence() {
        return sequence;
    }

    public String content() {
        return content;
    }

    public AgentToolExecutionEvent tool() {
        return tool;
    }

    public ChatClarificationEventBO clarification() {
        return clarification;
    }

    public ChatExecutionTimelineEventBO execution() {
        return execution;
    }

    public ChatErrorBO error() {
        return error;
    }
}
