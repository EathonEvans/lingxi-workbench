package com.lingxi.workbench.chat.service.impl;

import com.alibaba.fastjson2.JSON;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 默认的共享对话上下文准备用例。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextServiceImpl implements ConversationContextService {

    private final ConversationMemoryService conversationMemoryService;
    private final MemoryContextService memoryContextService;
    private final RuntimeVariablesInitializer runtimeVariablesInitializer;
    private final QueryRewriter queryRewriter;
    private final TokenCountEstimator tokenCountEstimator;

    /**
     * 为一次对话轮次准备共享的不可变上下文。
     *
     * <p>按固定顺序完成窗口记忆加载、会话摘要截断、长期记忆加载、运行时变量初始化和
     * 问题重写，最终组装为后续路由与执行共用的 {@link ConversationContext}。
     * 所有记忆内容都受单轮 Token 预算约束，重写调用还受 Application 总超时约束。</p>
     *
     * @param input 上下文准备输入（应用快照、原始问题、登录用户、对话轮次和起始时间）
     * @return 已准备的共享对话上下文
     */
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

        // ⑥ 问题重写：存在历史时消解指代；没有历史时保持原问题，避免无意义模型调用
        ContextualizationPolicy contextualizationPolicy = withinDeadline(contextPolicy.contextualization(), deadline);
        QueryRewriter.RewriteResult rewrite = queryRewriter.rewrite(contextualizationPolicy, query, runtimeVariables, memory.recentMessages());
        log.info("问题重写完成,源问题：{} -> 重写后:{}", query, JSON.toJSONString(rewrite));
        ensureWithinDeadline(deadline);

        // ⑦ 组装不可变 ConversationContext，共享给后续路由和执行
        return ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(query)
                .rewrittenQuery(rewrite.query())
                .recentMessages(memory.recentMessages())
                .summary(summary)
                .longTermMemory(longTermMemory)
                .budget(budget)
                .runtimeVariables(runtimeVariables)
                .preparationTokenUsage(rewrite.usage())
                .preparationModelCalls(rewrite.modelCalls())
                .build();
    }

    /**
     * 将问题重写策略的局部超时压缩到 Application 单轮硬截止之内。
     *
     * <p>重写超时必须与剩余总超时取较小值，避免局部调用越过整轮截止；
     * 剩余时间不足 1 秒时直接抛出超时异常。</p>
     *
     * @param policy   发布快照中的问题重写策略
     * @param deadline 本轮执行的总截止时间
     * @return 超时已压缩的重写策略（禁用时原样返回）
     */
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

    /**
     * 校验本轮执行仍在总截止时间之内，超时则抛出业务异常。
     *
     * @param deadline 本轮执行的总截止时间
     */
    private static void ensureWithinDeadline(Instant deadline) {
        remaining(deadline);
    }

    /**
     * 计算距离总截止时间剩余的时长，已过期时抛出超时异常。
     *
     * @param deadline 本轮执行的总截止时间
     * @return 剩余时长（保证为正）
     */
    private static Duration remaining(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw timeoutExceeded();
        }
        return remaining;
    }

    /**
     * 构造单轮执行超过总超时的统一业务异常。
     *
     * @return 冲突状态的超时异常
     */
    private static BusinessException timeoutExceeded() {
        return new BusinessException(HttpStatusEnum.CONFLICT, "Application单轮执行超过总超时");
    }

    /**
     * 将内部记忆消息转换为网关使用的历史消息列表。
     *
     * @param messages 窗口记忆中的消息列表
     * @return 网关历史消息列表（USER/ASSISTANT 角色映射）
     */
    private static List<ChatHistoryMessage> toHistory(List<ChatMemoryMessageBO> messages) {
        return messages.stream()
                .map(message -> new ChatHistoryMessage(switch (message.role()) {
                    case USER -> ChatHistoryMessage.Role.USER;
                    case ASSISTANT -> ChatHistoryMessage.Role.ASSISTANT;
                }, message.content()))
                .toList();
    }

    /**
     * 按记忆配置加载会话级上下文记忆（窗口消息 + 已有摘要）。
     *
     * <p>启用摘要时使用上下文记忆服务（窗口与摘要统一管理）；否则仅按轮次和
     * Token 上限截取近 N 轮窗口消息。</p>
     *
     * @param config 记忆配置
     * @param userId 当前用户
     * @param turn   当前对话轮次
     * @param budget 上下文 Token 预算
     * @return 已加载的会话记忆快照
     */
    private ConversationMemorySnapshot loadConversationMemory(MemoryConfig config, Long userId, ChatTurnBO turn, ContextBudgetPolicy budget) {
        if (config.summary().isEnabled()) {
            return conversationMemoryService.loadContextMemory(userId, turn, budget);
        }
        return new ConversationMemorySnapshot("", conversationMemoryService.getWindowMemory(
                userId, turn.conversationId(), turn.userSequenceNo(), budget.getMaxHistoryRounds(), budget.getMaxHistoryTokens()));
    }

    /**
     * 在剩余 Token 预算内加载长期记忆（用户画像 + 相关交互记录）。
     *
     * <p>仅当画像或交互记录任一启用且预算为正时才发起加载，结果按预算截断。</p>
     *
     * @param config  记忆配置
     * @param userId  当前用户
     * @param query   当前问题（用于检索相关交互记录）
     * @param maxTokens 长期记忆可用 Token 上限
     * @return 已截断的长期记忆文本（无可用预算或未启用时为空串）
     */
    private String loadLongTermMemory(MemoryConfig config, Long userId, String query, int maxTokens) {
        boolean hasLongTermMemory = config.profile().isEnabled() || config.interactionRecord().isEnabled();
        if (maxTokens <= 0 || !hasLongTermMemory) {
            return "";
        }
        return limitTokens(memoryContextService.build(userId, query, config), maxTokens);
    }

    /**
     * 按 Token 上限截断文本，使用二分法在字符边界处精确截断。
     *
     * <p>先去除首尾空白，若整体未超预算则原样返回；否则二分查找满足预算的
     * 最长前缀，并避免截断点落在代理对（surrogate pair）中间。</p>
     *
     * @param value    待截断的文本
     * @param maxTokens 允许的最大 Token 数
     * @return 截断后的文本（不超预算）
     */
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

    /**
     * 去除首尾空白并处理空值。
     *
     * @param value 原始文本（可能为 null）
     * @return 去除首尾空白后的文本（null 转为空串）
     */
    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
