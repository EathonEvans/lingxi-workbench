package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;

/** 模型生成任务向观察者发布的事件。 */
public record ChatGenerationEventBO(
        ChatGenerationEventType type,
        long sequence,
        String content) {

    public static ChatGenerationEventBO chunk(long sequence, String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.CHUNK, sequence, content);
    }

    public static ChatGenerationEventBO snapshot(String content) {
        return new ChatGenerationEventBO(ChatGenerationEventType.SNAPSHOT, 0, content);
    }

    public static ChatGenerationEventBO completed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.COMPLETED, 0, null);
    }

    public static ChatGenerationEventBO failed() {
        return new ChatGenerationEventBO(ChatGenerationEventType.FAILED, 0, null);
    }

    public static ChatGenerationEventBO cancelled() {
        return new ChatGenerationEventBO(ChatGenerationEventType.CANCELLED, 0, null);
    }
}
