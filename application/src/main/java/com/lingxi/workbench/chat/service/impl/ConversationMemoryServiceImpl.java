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
        if (conversationRepo.findOwnedById(conversationId, userId).isEmpty()) {
            throw new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在");
        }

        int maxRounds = Math.max(properties.getMaxRounds(), 0);
        int maxTokens = Math.max(properties.getMaxTokens(), 0);
        if (maxRounds == 0 || maxTokens == 0) {
            return List.of();
        }

        List<ConversationMessageEntity> messages = messageRepo.findRecentCompletedRounds(conversationId, beforeSequenceNo, maxRounds);
        if (messages.isEmpty()) {
            return List.of();
        }

        List<ChatMemoryMessageBO> selectedReversed = new ArrayList<>();
        int usedTokens = 0;
        // 必须按完整问答轮次从新到旧裁剪，避免只携带半轮上下文。
        for (int index = messages.size() - 2; index >= 0; index -= 2) {
            ConversationMessageEntity userMessage = messages.get(index);
            ConversationMessageEntity assistantMessage = messages.get(index + 1);
            int roundTokens = estimate(userMessage) + estimate(assistantMessage);
            if (usedTokens + roundTokens > maxTokens) {
                break;
            }
            selectedReversed.add(new ChatMemoryMessageBO(assistantMessage.getRole(), assistantMessage.getContent()));
            selectedReversed.add(new ChatMemoryMessageBO(userMessage.getRole(), userMessage.getContent()));
            usedTokens += roundTokens;
        }

        Collections.reverse(selectedReversed);
        return List.copyOf(selectedReversed);
    }

    private int estimate(ConversationMessageEntity message) {
        return tokenCountEstimator.estimate(message.getContent()) + MESSAGE_OVERHEAD_TOKENS;
    }
}
