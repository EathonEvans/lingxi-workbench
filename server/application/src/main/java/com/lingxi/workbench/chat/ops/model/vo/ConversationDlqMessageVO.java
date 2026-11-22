package com.lingxi.workbench.chat.ops.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 不包含原始对话内容的会话记忆死信摘要。 */
@Getter
@Builder
public class ConversationDlqMessageVO {
    private final String group;
    private final String groupName;
    private final String msgId;
    private final String eventId;
    private final String conversationId;
    private final String assistantMessageId;
    private final String appVersionId;
    private final int reconsumeTimes;
    private final OffsetDateTime storedAt;
}
