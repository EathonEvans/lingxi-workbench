package com.lingxi.workbench.admin.appmonitor.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 管理端可见的会话记忆死信定位摘要，不包含原始对话正文。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDlqMessageVO {
    private String group;
    private String groupName;
    private String msgId;
    private String eventId;
    private String conversationId;
    private String assistantMessageId;
    private String appVersionId;
    private int reconsumeTimes;
    private OffsetDateTime storedAt;
}
