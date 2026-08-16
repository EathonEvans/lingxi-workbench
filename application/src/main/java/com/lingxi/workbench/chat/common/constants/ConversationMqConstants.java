package com.lingxi.workbench.chat.common.constants;

/** 会话事务事件 Topic 与消费者组常量。 */
public final class ConversationMqConstants {

    public static final String EVENT_TX_TOPIC = "lingxi-conversation-event-tx";
    public static final String SUMMARY_CONSUMER_GROUP = "lingxi-conversation-summary-consumer";
    public static final String PROFILE_CONSUMER_GROUP = "lingxi-user-profile-consumer";
    public static final String INTERACTION_CONSUMER_GROUP = "lingxi-interaction-record-consumer";
    public static final String HITL_RESUME_TOPIC = "lingxi-hitl-resume-tx";
    public static final String HITL_RESUME_CONSUMER_GROUP = "lingxi-hitl-resume-consumer";
    public static final int MAX_RECONSUME_TIMES = 5;

    private ConversationMqConstants() {
    }
}
