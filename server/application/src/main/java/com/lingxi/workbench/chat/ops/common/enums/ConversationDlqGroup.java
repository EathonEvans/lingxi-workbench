package com.lingxi.workbench.chat.ops.common.enums;

import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 允许运维查询和重放的三个会话记忆消费组。 */
@Getter
@RequiredArgsConstructor
public enum ConversationDlqGroup {
    SUMMARY("SUMMARY", ConversationMqConstants.SUMMARY_CONSUMER_GROUP, "会话摘要"),
    PROFILE("PROFILE", ConversationMqConstants.PROFILE_CONSUMER_GROUP, "用户画像"),
    INTERACTION("INTERACTION", ConversationMqConstants.INTERACTION_CONSUMER_GROUP, "交互记录");

    private final String code;
    private final String consumerGroup;
    private final String displayName;
}
