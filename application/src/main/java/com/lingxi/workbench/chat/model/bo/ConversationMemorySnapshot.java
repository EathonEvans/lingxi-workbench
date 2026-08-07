package com.lingxi.workbench.chat.model.bo;

import java.util.List;
import java.util.Objects;

/**
 * 单轮共享上下文准备阶段读取的会话记忆快照。
 *
 * @param summary        已持久化且生效的会话摘要
 * @param recentMessages 摘要覆盖范围之后的最近完整问答消息
 */
public record ConversationMemorySnapshot(String summary, List<ChatMemoryMessageBO> recentMessages) {

    public ConversationMemorySnapshot {
        summary = Objects.requireNonNullElse(summary, "").trim();
        recentMessages = List.copyOf(Objects.requireNonNullElse(recentMessages, List.of()));
    }
}
