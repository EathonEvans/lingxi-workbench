package com.lingxi.workbench.chat.ops.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

/** 会话事务消息运维总览。 */
@Getter
@Builder
public class ConversationOpsOverviewVO {
    private final boolean mqAvailable;
    private final String mqMessage;
    private final OffsetDateTime checkedAt;
    private final long staleGenerating;
    private final long pendingMemoryReconcile;
    private final long dlqTotal;
    private final Map<String, Long> dlqByGroup;
}
