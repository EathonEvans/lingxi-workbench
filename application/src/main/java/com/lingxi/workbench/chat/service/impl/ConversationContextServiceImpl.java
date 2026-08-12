package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.memory.MemoryContextService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationContextPrepareBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    public ConversationContext prepare(ConversationContextPrepareBO input) {
        AppRuntimeSnapshot application = input.application();
        String query = input.query();
        LoginUser loginUser = input.loginUser();
        ChatTurnBO turn = input.turn();
        ApplicationRuntimePolicy runtimePolicy = application.definition().runtimePolicy();
        Instant deadline = input.startedAt().plusSeconds(runtimePolicy.getTimeoutSeconds());

        // ① 从 Application 发布快照读取上下文策略和 Token 预算
        ApplicationContextPolicy contextPolicy = application.definition().contextPolicy();
        ContextBudgetPolicy budget = contextPolicy.budget();

        // ② 加载窗口记忆：启用摘要时走 ContextMemory（窗口+已有摘要），否则只取近 N 轮
        MemoryConfig memoryConfig = application.definition().memoryConf();
        ConversationMemorySnapshot memory = loadConversationMemory(memoryConfig, loginUser.id(), turn, budget);

        // ③ 会话摘要：启用时按 maxMemoryTokens 截断（Token 级别二分截断，非简单字符截断）
        String summary = memoryConfig.summary().isEnabled()
                ? limitTokens(memory.summary(), budget.getMaxMemoryTokens())
                : "";

        // ④ 长期记忆：扣除摘要 token 后的剩余预算内，加载画像 + 相关交互记录
        int remainingMemoryTokens = Math.max(budget.getMaxMemoryTokens() - tokenCountEstimator.estimate(summary), 0);
        String longTermMemory = loadLongTermMemory(memoryConfig, loginUser.id(), query, remainingMemoryTokens);

        // ⑤ 初始化运行时变量容器，注入会话级变量
        RuntimeVariables runtimeVariables = runtimeVariablesInitializer.initialize(loginUser, query, turn.conversationId());
        runtimeVariables.put(RuntimeVariableAccessors.SESSION_HISTORY, toHistory(memory.recentMessages()));
        if (!summary.isBlank()) {
            runtimeVariables.put(RuntimeVariableAccessors.SESSION_SUMMARY, summary);
        }
        if (!longTermMemory.isBlank()) {
            runtimeVariables.put(RuntimeVariableAccessors.LONG_TERM_MEMORY, longTermMemory);
        }

        // ⑥ Query 重写：有摘要时用摘要补充语义，无摘要时保持原文
        ContextualizationPolicy contextualizationPolicy = withinDeadline(contextPolicy.contextualization(), deadline);
        QueryContextualizer.ContextualizationResult contextualization = queryContextualizer
                .contextualize(contextualizationPolicy, query, runtimeVariables, memory.recentMessages());
        ensureWithinDeadline(deadline);

        // ⑦ 组装不可变 ConversationContext，共享给后续路由和执行
        return ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(query)
                .contextualizedQuery(contextualization.query())
                .recentMessages(memory.recentMessages())
                .summary(summary)
                .longTermMemory(longTermMemory)
                .budget(budget)
                .runtimeVariables(runtimeVariables)
                .preparationTokenUsage(contextualization.usage())
                .preparationModelCalls(contextualization.modelCalls())
                .build();
    }

    /** 上下文化局部超时不得越过Application单轮硬截止。 */
    private static ContextualizationPolicy withinDeadline(ContextualizationPolicy policy, Instant deadline) {
        Duration remaining = remaining(deadline);
        if (!policy.isEnabled()) {
            return policy;
        }
        int timeoutSeconds = Math.min(policy.effectiveTimeoutSeconds(), Math.toIntExact(remaining.toSeconds()));
        if (timeoutSeconds < 1) {
            throw timeoutExceeded();
        }
        return ContextualizationPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(policy.getChatModelInstanceId())
                .promptVersionId(policy.getPromptVersionId())
                .fallbackToOriginal(policy.isFallbackToOriginal())
                .maxOutputTokens(policy.effectiveMaxOutputTokens())
                .maxOutputCharacters(policy.effectiveMaxOutputCharacters())
                .timeoutSeconds(timeoutSeconds)
                .build();
    }

    private static void ensureWithinDeadline(Instant deadline) {
        remaining(deadline);
    }

    private static Duration remaining(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw timeoutExceeded();
        }
        return remaining;
    }

    private static BusinessException timeoutExceeded() {
        return new BusinessException(HttpStatusEnum.CONFLICT, "Application单轮执行超过总超时");
    }

    private static List<ChatHistoryMessage> toHistory(List<ChatMemoryMessageBO> messages) {
        return messages.stream()
                .map(message -> new ChatHistoryMessage(switch (message.role()) {
                    case USER -> ChatHistoryMessage.Role.USER;
                    case ASSISTANT -> ChatHistoryMessage.Role.ASSISTANT;
                }, message.content()))
                .toList();
    }

    private ConversationMemorySnapshot loadConversationMemory(MemoryConfig config, Long userId, ChatTurnBO turn, ContextBudgetPolicy budget) {
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

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
