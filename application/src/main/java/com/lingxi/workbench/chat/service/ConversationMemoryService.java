package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;

import java.util.List;

/**
 * 会话记忆业务。
 */
public interface ConversationMemoryService {

    /**
     * 获取当前轮之前可注入模型的窗口记忆。
     * 返回结果按消息产生时间正序排列。
     */
    List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo);
}
