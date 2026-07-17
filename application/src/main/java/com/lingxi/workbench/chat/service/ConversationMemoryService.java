package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;

import java.util.List;

/**
 * 会话记忆业务。
 */
public interface ConversationMemoryService {

    /**
     * 获取当前轮之前可注入模型的窗口记忆。
     *
     * <p>只选择已经完成的“用户消息 + Assistant 消息”完整轮次，随后同时按照最大轮数和
     * Token 预算从近到远裁剪，返回结果再恢复为消息产生时间正序。</p>
     *
     * @param userId 当前登录用户主键，用于校验会话归属
     * @param conversationId 会话主键
     * @param beforeSequenceNo 当前用户消息序号，只查询该序号之前的历史消息
     * @return 可直接注入模型上下文的历史消息，按产生时间正序排列
     */
    List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo);
}
