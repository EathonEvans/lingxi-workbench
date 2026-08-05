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

    private ChatGenerationEventBO(
            ChatGenerationEventType type,
            long sequence,
            String content,
            AgentToolExecutionEvent tool,
            ChatClarificationEventBO clarification) {
        this.type = type;
        this.sequence = sequence;
        this.content = content;
        this.tool = tool;
        this.clarification = clarification;
    }

    public static ChatGenerationEventBO chunk(long sequence, String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.CHUNK, sequence, content, null, null);
    }

    public static ChatGenerationEventBO tool(long sequence, AgentToolExecutionEvent tool) {
        return new ChatGenerationEventBO(ChatGenerationEventType.TOOL, sequence, null, tool, null);
    }

    public static ChatGenerationEventBO clarification(long sequence, ChatClarificationEventBO clarification) {
        return new ChatGenerationEventBO(ChatGenerationEventType.CLARIFICATION, sequence, null, null, clarification);
    }

    public static ChatGenerationEventBO snapshot(String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.SNAPSHOT, 0, content, null, null);
    }

    public static ChatGenerationEventBO completed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.COMPLETED, 0, null, null, null);
    }

    public static ChatGenerationEventBO failed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.FAILED, 0, null, null, null);
    }

    public static ChatGenerationEventBO cancelled() {
        return new ChatGenerationEventBO(ChatGenerationEventType.CANCELLED, 0, null, null, null);
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
}
