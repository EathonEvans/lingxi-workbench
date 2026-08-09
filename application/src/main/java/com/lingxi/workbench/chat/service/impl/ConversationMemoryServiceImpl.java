package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.config.ChatMemoryProperties;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.bo.ConversationSummaryTrigger;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    public ConversationMemorySnapshot loadContextMemory(Long userId, ChatTurnBO turn, ContextBudgetPolicy budget) {
        ConversationEntity conversation = findOwnedConversation(turn.conversationId(), userId);
        List<ChatMemoryMessageBO> messages = loadWindowMemory(turn.conversationId(), turn.userSequenceNo(), budget.getMaxHistoryRounds(), budget.getMaxHistoryTokens());
        String summary = Objects.requireNonNullElse(conversation.getSummary(), "").trim();
        return new ConversationMemorySnapshot(summary, removeSummaryCoveredMessages(messages, summary, conversation.getSummarySequenceNo()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationSummarySource> loadSummarySource(Long userId, ChatTurnBO turn, ConversationSummaryTrigger trigger) {
        if (trigger.minNewRounds() <= 0 || trigger.minNewTokens() <= 0 || trigger.maxInputTokens() <= 0) {
            throw new IllegalArgumentException("摘要触发轮数、Token阈值和输入上限必须大于0");
        }
        ConversationEntity conversation = findOwnedConversation(turn.conversationId(), userId);
        int previousCoveredSequenceNo = Objects.requireNonNullElse(conversation.getSummarySequenceNo(), 0);
        List<ConversationMessageEntity> messages = messageRepo.findCompletedRoundsAfter(
                turn.conversationId(), previousCoveredSequenceNo, turn.userSequenceNo(), trigger.minNewRounds());
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        List<ChatMemoryMessageBO> sourceMessages = messages.stream()
                .map(message -> new ChatMemoryMessageBO(message.getSequenceNo(), message.getRole(), message.getContent()))
                .toList();
        int completeRounds = sourceMessages.size() / 2;
        int newMessageTokens = sourceMessages.stream().mapToInt(this::estimate).sum();
        if (completeRounds < trigger.minNewRounds() && newMessageTokens < trigger.minNewTokens()) {
            return Optional.empty();
        }
        String previousSummary = Objects.requireNonNullElse(conversation.getSummary(), "").trim();
        List<ChatMemoryMessageBO> selectedMessages = selectSummaryBatch(sourceMessages, previousSummary, trigger.maxInputTokens());
        Integer coveredUserSequenceNo = selectedMessages.get(selectedMessages.size() - 2).sequenceNo();
        return Optional.of(new ConversationSummarySource(
                previousSummary, conversation.getSummarySequenceNo(), coveredUserSequenceNo, selectedMessages));
    }

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
        findOwnedConversation(conversationId, userId);

        // 配置出现负数时按 0 处理；任一限制为 0 都代表本次关闭历史记忆。
        int maxRounds = Math.max(configuredMaxRounds, 0);
        int maxTokens = Math.max(configuredMaxTokens, 0);
        return loadWindowMemory(conversationId, beforeSequenceNo, maxRounds, maxTokens);
    }

    private List<ChatMemoryMessageBO> loadWindowMemory(Long conversationId, Integer beforeSequenceNo, int maxRounds, int maxTokens) {
        if (maxRounds == 0 || maxTokens == 0) {
            return List.of();
        }

        // Repo 只返回当前轮之前、状态完整且角色配对的最近问答轮次，结果按消息序号正序排列。
        List<ConversationMessageEntity> messages = messageRepo.findRecentCompletedRounds(conversationId, beforeSequenceNo, maxRounds);
        if (messages.isEmpty()) {
            return List.of();
        }

        List<ChatMemoryMessageBO> loadedMessages = messages.stream()
                .map(message -> new ChatMemoryMessageBO(message.getSequenceNo(), message.getRole(), message.getContent()))
                .toList();
        return selectWindow(loadedMessages, maxRounds, maxTokens);
    }

    @Override
    public List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages) {
        return selectWindow(messages, properties.getMaxRounds(), properties.getMaxTokens());
    }

    @Override
    public List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages, int configuredMaxRounds, int configuredMaxTokens) {
        int maxRounds = Math.max(configuredMaxRounds, 0);
        int maxTokens = Math.max(configuredMaxTokens, 0);
        if (messages.isEmpty() || maxRounds == 0 || maxTokens == 0) {
            return List.of();
        }
        List<ChatMemoryMessageBO> selectedReversed = new ArrayList<>();
        int usedTokens = 0;
        int selectedRounds = 0;
        for (int index = messages.size() - 2; index >= 0 && selectedRounds < maxRounds; index -= 2) {
            ChatMemoryMessageBO userMessage = messages.get(index);
            ChatMemoryMessageBO assistantMessage = messages.get(index + 1);
            int roundTokens = estimate(userMessage) + estimate(assistantMessage);
            if (usedTokens + roundTokens > maxTokens) {
                break;
            }
            selectedReversed.add(assistantMessage);
            selectedReversed.add(userMessage);
            usedTokens += roundTokens;
            selectedRounds++;
        }
        Collections.reverse(selectedReversed);
        return List.copyOf(selectedReversed);
    }

    private ConversationEntity findOwnedConversation(Long conversationId, Long userId) {
        return conversationRepo.findOwnedById(conversationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
    }

    /**
     * 摘要覆盖的完整问答轮次不再进入最近消息窗口。
     */
    private static List<ChatMemoryMessageBO> removeSummaryCoveredMessages(List<ChatMemoryMessageBO> messages, String summary, Integer summarySequenceNo) {
        if (summary.isBlank() || Objects.isNull(summarySequenceNo)) {
            return messages;
        }
        long coveredAssistantSequence = (long) summarySequenceNo + 1L;
        return messages.stream()
                .filter(message -> Objects.isNull(message.sequenceNo()) || message.sequenceNo() > coveredAssistantSequence)
                .toList();
    }

    /**
     * 估算消息文本 Token，并为角色名及消息协议结构预留固定开销。
     */
    private int estimate(ChatMemoryMessageBO message) {
        return tokenCountEstimator.estimate(message.content()) + MESSAGE_OVERHEAD_TOKENS;
    }

    /**
     * 从最早未覆盖轮次开始连续选择，确保滚动摘要不会在历史中间留下空洞。
     */
    private List<ChatMemoryMessageBO> selectSummaryBatch(List<ChatMemoryMessageBO> messages, String previousSummary, int maxTokens) {
        int usedTokens = tokenCountEstimator.estimate(previousSummary);
        List<ChatMemoryMessageBO> selected = new ArrayList<>();
        for (int index = 0; index + 1 < messages.size(); index += 2) {
            ChatMemoryMessageBO userMessage = messages.get(index);
            ChatMemoryMessageBO assistantMessage = messages.get(index + 1);
            int roundTokens = estimate(userMessage) + estimate(assistantMessage);
            if (usedTokens + roundTokens > maxTokens) {
                break;
            }
            selected.add(userMessage);
            selected.add(assistantMessage);
            usedTokens += roundTokens;
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("最早未摘要问答超过会话摘要Token预算");
        }
        return List.copyOf(selected);
    }
}
