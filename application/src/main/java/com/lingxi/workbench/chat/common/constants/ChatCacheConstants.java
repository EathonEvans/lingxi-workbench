package com.lingxi.workbench.chat.common.constants;

/**
 * 对话模块 Redis Key 命名空间。
 */
public interface ChatCacheConstants {

    /** 单次生成任务的可重放增量事件。 */
    String GENERATION_EVENT_KEY = "chat:generation:event";
}
