package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.impl.RuntimeVariablesInitializer;
import com.lingxi.workbench.chat.memory.MemoryContextService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.Objects;

/** 默认的共享对话上下文准备用例。 */
@Service
@RequiredArgsConstructor
public class ConversationContextServiceImpl implements ConversationContextService {

    private final ConversationMemoryService conversationMemoryService;
    private final MemoryContextService memoryContextService;
    private final RuntimeVariablesInitializer runtimeVariablesInitializer;
    private final QueryContextualizer queryContextualizer;
    private final TokenCountEstimator tokenCountEstimator;

    @Override
    public ConversationContext prepare(ApplicationRuntimeSnapshot application, String query, LoginUser loginUser, ChatTurnBO turn) {
        ApplicationContextPolicy contextPolicy = application.definition().contextPolicy();
        ContextBudget budget = buildBudget(contextPolicy.budget());
        validateInputBudget(query, budget);
        MemoryConfig memoryConfig = application.definition().memory();
        ConversationMemorySnapshot memory = loadConversationMemory(memoryConfig, loginUser.id(), turn, budget);
        String summary = memoryConfig.summary().isEnabled()
                ? limitTokens(memory.summary(), budget.getMaxMemoryTokens())
                : "";
        int remainingMemoryTokens = Math.max(budget.getMaxMemoryTokens() - tokenCountEstimator.estimate(summary), 0);
        String longTermMemory = loadLongTermMemory(memoryConfig, loginUser.id(), query, remainingMemoryTokens);
        RuntimeVariables runtimeVariables = runtimeVariablesInitializer.initialize(loginUser, query, turn.conversationId());
        if (!summary.isBlank()) {
            runtimeVariables.put(RuntimeVariableAccessors.SESSION_SUMMARY, summary);
        }
        if (!longTermMemory.isBlank()) {
            runtimeVariables.put(RuntimeVariableAccessors.LONG_TERM_MEMORY, longTermMemory);
        }
        QueryContextualizer.ContextualizationResult contextualization = queryContextualizer.contextualize(
                contextPolicy.contextualization(), query, runtimeVariables, memory.recentMessages());

        return ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(query)
                // 原问题用于审计和回答，上下文化问题只用于路由与后续规划。
                .contextualizedQuery(contextualization.query())
                .recentMessages(memory.recentMessages())
                .summary(summary)
                .longTermMemory(longTermMemory)
                .budget(budget)
                .runtimeVariables(runtimeVariables)
                .preparationTokenUsage(contextualization.usage())
                .build();
    }

    private ConversationMemorySnapshot loadConversationMemory(MemoryConfig config, Long userId, ChatTurnBO turn, ContextBudget budget) {
        if (config.summary().isEnabled()) {
            return conversationMemoryService.loadContextMemory(userId, turn, budget);
        }
        return new ConversationMemorySnapshot("", conversationMemoryService.getWindowMemory(
                userId, turn.conversationId(), turn.userSequenceNo(), budget.getMaxHistoryRounds(), budget.getMaxHistoryTokens()));
    }

    private String loadLongTermMemory(MemoryConfig config, Long userId, String query, int maxTokens) {
        boolean hasLongTermMemory = config.profile().isEnabled() || config.interactionRecord().isEnabled();
        if (maxTokens <= 0 || !hasLongTermMemory) {
            return "";
        }
        return limitTokens(memoryContextService.build(userId, query, config), maxTokens);
    }

    private String limitTokens(String value, int maxTokens) {
        String normalized = normalize(value);
        if (normalized.isBlank() || tokenCountEstimator.estimate(normalized) <= maxTokens) {
            return normalized;
        }
        int lower = 0;
        int upper = normalized.length();
        while (lower < upper) {
            int middle = (lower + upper + 1) / 2;
            if (tokenCountEstimator.estimate(normalized.substring(0, middle)) <= maxTokens) {
                lower = middle;
            } else {
                upper = middle - 1;
            }
        }
        int end = lower;
        if (end > 0 && Character.isHighSurrogate(normalized.charAt(end - 1))) {
            end--;
        }
        return normalized.substring(0, end).trim();
    }

    private void validateInputBudget(String query, ContextBudget budget) {
        if (tokenCountEstimator.estimate(query) > budget.getMaxInputTokens()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "用户输入超过Application允许的Token预算");
        }
    }

    private static ContextBudget buildBudget(ContextBudgetPolicy policy) {
        return ContextBudget.builder()
                .maxHistoryRounds(policy.getMaxHistoryRounds())
                .maxSystemPromptTokens(policy.getMaxSystemPromptTokens())
                .maxCapabilityTokens(policy.getMaxCapabilityTokens())
                .maxHistoryTokens(policy.getMaxHistoryTokens())
                .maxMemoryTokens(policy.getMaxMemoryTokens())
                .maxInputTokens(policy.getMaxInputTokens())
                .maxObservationTokens(policy.getMaxObservationTokens())
                .reservedOutputTokens(policy.getReservedOutputTokens())
                .build();
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
