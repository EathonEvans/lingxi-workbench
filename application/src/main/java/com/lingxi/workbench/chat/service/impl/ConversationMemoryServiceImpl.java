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

    @Override
    @Transactional(readOnly = true)
    public List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo) {
        // 记忆查询同样必须校验会话归属，不能仅依赖上游已经执行过权限校验。
        if (conversationRepo.findOwnedById(conversationId, userId).isEmpty()) {
            throw new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在");
        }

        // 配置出现负数时按 0 处理；任一限制为 0 都代表本次关闭历史记忆。
        int maxRounds = Math.max(properties.getMaxRounds(), 0);
        int maxTokens = Math.max(properties.getMaxTokens(), 0);
        if (maxRounds == 0 || maxTokens == 0) {
            return List.of();
        }

        // Repo 只返回当前轮之前、状态完整且角色配对的最近问答轮次，结果按消息序号正序排列。
        List<ConversationMessageEntity> messages = messageRepo.findRecentCompletedRounds(
                conversationId, beforeSequenceNo, maxRounds);
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

    /** 估算消息文本 Token，并为角色名及消息协议结构预留固定开销。 */
    private int estimate(ConversationMessageEntity message) {
        return tokenCountEstimator.estimate(message.getContent()) + MESSAGE_OVERHEAD_TOKENS;
    }
}
