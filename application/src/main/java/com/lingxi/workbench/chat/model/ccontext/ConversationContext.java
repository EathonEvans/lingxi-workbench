package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * 路由、规划和执行共同消费的单轮可信对话上下文。
 *
 * <p>原始问题始终保留；{@code rewrittenQuery} 是供路由和规划使用的重写问题。
 * 当前 Application 快照尚未提供问题重写策略时，两者保持一致，后续接入模型时也不得
 * 覆盖原始输入。</p>
 */
@Getter
public final class ConversationContext {

    private final AppRuntimeSnapshot applicationSnapshot;
    private final LoginUser loginUser;
    private final ChatTurnBO turn;
    private final String originalQuery;
    private final String rewrittenQuery;
    private final List<ChatMemoryMessageBO> recentMessages;
    private final String summary;
    private final String longTermMemory;
    private final ContextBudgetPolicy budget;
    private final RuntimeVariables runtimeVariables;
    private final TokenUsage preparationTokenUsage;
    private final int preparationModelCalls;

    @Builder
    private ConversationContext(AppRuntimeSnapshot applicationSnapshot, LoginUser loginUser, ChatTurnBO turn, String originalQuery, String rewrittenQuery, List<ChatMemoryMessageBO> recentMessages, String summary, String longTermMemory, ContextBudgetPolicy budget, RuntimeVariables runtimeVariables, TokenUsage preparationTokenUsage, int preparationModelCalls) {
        this.applicationSnapshot = Objects.requireNonNull(applicationSnapshot, "Application运行快照不能为空");
        this.loginUser = Objects.requireNonNull(loginUser, "登录用户不能为空");
        this.turn = Objects.requireNonNull(turn, "对话轮次不能为空");
        this.originalQuery = requireText(originalQuery, "用户原始问题");
        this.rewrittenQuery = requireText(rewrittenQuery, "重写问题");
        this.recentMessages = List.copyOf(Objects.requireNonNullElse(recentMessages, List.of()));
        this.summary = Objects.requireNonNullElse(summary, "");
        this.longTermMemory = Objects.requireNonNullElse(longTermMemory, "");
        this.budget = Objects.requireNonNull(budget, "共享上下文预算不能为空");
        this.runtimeVariables = Objects.requireNonNull(runtimeVariables, "运行时变量不能为空");
        this.preparationTokenUsage = Objects.requireNonNullElseGet(preparationTokenUsage, TokenUsage::empty);
        this.preparationModelCalls = preparationModelCalls;
    }

    /**
     * 返回意图路由和后续规划使用的问题。
     */
    public String routingQuery() {
        return rewrittenQuery;
    }

    /**
     * 为跨消息Planner澄清恢复新的有效任务，同时保留同一份已准备上下文数据。
     */
    public ConversationContext withRoutingQuery(String query) {
        return ConversationContext.builder()
                .applicationSnapshot(applicationSnapshot)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(originalQuery)
                .rewrittenQuery(query)
                .recentMessages(recentMessages)
                .summary(summary)
                .longTermMemory(longTermMemory)
                .budget(budget)
                .runtimeVariables(runtimeVariables.forkForQuery(query))
                .preparationTokenUsage(preparationTokenUsage)
                .preparationModelCalls(preparationModelCalls)
                .build();
    }

    private static String requireText(String value, String fieldName) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
