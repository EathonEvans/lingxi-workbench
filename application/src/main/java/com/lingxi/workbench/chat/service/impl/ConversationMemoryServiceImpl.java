package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.config.ChatMemoryProperties;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于已落库完整问答轮次的窗口记忆实现。
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    /**
     * 每条消息角色和协议结构预留的少量 Token。
     */
    private static final int MESSAGE_OVERHEAD_TOKENS = 8;

    private final ConversationRepo conversationRepo;
    private final ConversationMessageRepo messageRepo;
    private final ChatMemoryProperties properties;
    private final TokenCountEstimator tokenCountEstimator;

    /**
     * 获取当前轮之前可注入模型的窗口记忆。
     *
     * <p>只选择已经完成的完整问答轮次，按轮数和 Token 预算从近到远裁剪，
     * 最终返回按时间正序排列的历史消息。</p>
     *
     * @param userId           当前登录用户主键，用于校验会话归属
     * @param conversationId   会话主键
     * @param beforeSequenceNo 当前用户消息序号，只查询该序号之前的历史消息
     * @return 可直接注入模型上下文的历史消息，按产生时间正序排列
     */
    /**
     * 使用默认配置的窗口记忆限制读取历史消息。
     * 轮数和 Token 上限均取自 ChatMemoryProperties 配置。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo) {
        return getWindowMemory(userId, conversationId, beforeSequenceNo, properties.getMaxRounds(), properties.getMaxTokens());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo, int configuredMaxRounds, int configuredMaxTokens) {
        // 记忆查询同样必须校验会话归属，不能仅依赖上游已经执行过权限校验。
        if (conversationRepo.findOwnedById(conversationId, userId).isEmpty()) {
            throw new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在");
        }

        // 配置出现负数时按 0 处理；任一限制为 0 都代表本次关闭历史记忆。
        int maxRounds = Math.max(configuredMaxRounds, 0);
        int maxTokens = Math.max(configuredMaxTokens, 0);
        if (maxRounds == 0 || maxTokens == 0) {
            return List.of();
        }

        // Repo 只返回当前轮之前、状态完整且角色配对的最近问答轮次，结果按消息序号正序排列。
        List<ConversationMessageEntity> messages = messageRepo.findRecentCompletedRounds(conversationId, beforeSequenceNo, maxRounds);
        if (messages.isEmpty()) {
            return List.of();
        }

        // 从最新轮次向前选择，保证 Token 不足时优先保留离当前问题最近的上下文。
        List<ChatMemoryMessageBO> selectedReversed = new ArrayList<>();
        int usedTokens = 0;
        // 必须按完整问答轮次从新到旧裁剪，避免只携带半轮上下文。
        for (int index = messages.size() - 2; index >= 0; index -= 2) {
            ConversationMessageEntity userMessage = messages.get(index);
            ConversationMessageEntity assistantMessage = messages.get(index + 1);
            int roundTokens = estimate(userMessage) + estimate(assistantMessage);
            if (usedTokens + roundTokens > maxTokens) {
                // 不拆分问答轮次；当前轮放不下时直接停止继续向更早历史扩展。
                break;
            }
            // 当前正在反向遍历，先加入 Assistant 再加入 User，最终统一 reverse 恢复时间正序。
            selectedReversed.add(new ChatMemoryMessageBO(assistantMessage.getRole(), assistantMessage.getContent()));
            selectedReversed.add(new ChatMemoryMessageBO(userMessage.getRole(), userMessage.getContent()));
            usedTokens += roundTokens;
        }

        // 模型上下文必须按照真实对话顺序组织：User → Assistant → User → Assistant。
        Collections.reverse(selectedReversed);
        return List.copyOf(selectedReversed);
    }

    /**
     * 估算消息文本 Token，并为角色名及消息协议结构预留固定开销。
     */
    private int estimate(ConversationMessageEntity message) {
        return tokenCountEstimator.estimate(message.getContent()) + MESSAGE_OVERHEAD_TOKENS;
    }
}
