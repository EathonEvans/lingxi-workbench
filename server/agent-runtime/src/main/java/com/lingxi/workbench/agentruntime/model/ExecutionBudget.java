package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lingxi 对一次 AgentScope 调用保留的预算与用量账本。
 *
 * <p>Agent 的消息循环和生命周期由 AgentScope 驱动；该对象只负责 Lingxi 发布版本约束中的
 * 模型、Tool、Token 硬边界，以及跨子 Agent 调用的实际用量合并。</p>
 */
public final class ExecutionBudget {

    private final ExecutionPolicy policy;
    private final AtomicInteger modelCalls;
    private final AtomicInteger toolCalls;
    private final TokenUsageAccumulator tokenUsage = new TokenUsageAccumulator();
    private boolean finalizationRequested;
    private String finalizationReason;

    public ExecutionBudget(ExecutionPolicy policy, int initialModelCalls, int initialToolCalls,
                           TokenUsage initialUsage) {
        this.policy = Objects.requireNonNull(policy, "Agent执行策略不能为空");
        if (initialModelCalls < 0 || initialToolCalls < 0
                || initialModelCalls > policy.getMaxModelCalls()
                || initialToolCalls > policy.getMaxToolCalls()) {
            throw new IllegalArgumentException("Agent初始调用计数不合法");
        }
        this.modelCalls = new AtomicInteger(initialModelCalls);
        this.toolCalls = new AtomicInteger(initialToolCalls);
        tokenUsage.initialize(initialUsage);
    }

    /** 进入一次普通模型调用前原子占用额度。 */
    public synchronized int reserveModelCall() {
        ensureTokenCapacity(policy.getMaxTotalTokens(), "AGENT_TOKEN_LIMIT");
        int callNo = modelCalls.incrementAndGet();
        if (callNo > policy.getMaxModelCalls()) {
            modelCalls.decrementAndGet();
            throw new AgentRuntimeLimitException("AGENT_MODEL_CALL_LIMIT", "Agent工作模型调用次数已达到上限");
        }
        return callNo;
    }

    /** 最终模型调用使用硬 Token 上限，但仍共享发布版本的模型调用总额度。 */
    public synchronized int reserveFinalModelCall() {
        ensureTokenCapacity(policy.getMaxFinalizationTokens(), "AGENT_TOKEN_HARD_LIMIT");
        int callNo = modelCalls.incrementAndGet();
        if (callNo > policy.getMaxModelCalls()) {
            modelCalls.decrementAndGet();
            throw new AgentRuntimeLimitException("AGENT_MODEL_CALL_LIMIT", "Agent最终模型调用次数已达到上限");
        }
        return callNo;
    }

    public synchronized void completeModelCall(TokenUsage usage) {
        tokenUsage.add(usage);
    }

    /** Tool 与 Supervisor 能力调用共用同一全局调用预算。 */
    public synchronized void reserveToolCall(String reason) {
        if (toolCalls.get() >= policy.getMaxToolCalls()) {
            requestFinalization(reason);
            throw new AgentRuntimeLimitException("AGENT_TOOL_CALL_LIMIT", "Agent工具调用次数已达到上限");
        }
        toolCalls.incrementAndGet();
    }

    /** 合并隔离子 Agent 已发生的实际用量，不能把估算值写入运行账本。 */
    public synchronized void recordDelegatedUsage(int delegatedModelCalls, int delegatedToolCalls, TokenUsage usage) {
        if (delegatedModelCalls < 0 || delegatedToolCalls < 0) {
            throw new IllegalArgumentException("委派调用计数不能小于0");
        }
        modelCalls.addAndGet(delegatedModelCalls);
        toolCalls.addAndGet(delegatedToolCalls);
        tokenUsage.add(usage);
    }

    public synchronized boolean requestFinalization(String reason) {
        if (finalizationRequested) {
            return false;
        }
        finalizationRequested = true;
        finalizationReason = Optional.ofNullable(reason).filter(value -> !value.isBlank())
                .orElse("运行预算已到边界或工具结果已经收敛");
        return true;
    }

    public synchronized void restoreFinalization(AgentScopeCheckpoint checkpoint) {
        finalizationRequested = Boolean.TRUE.equals(checkpoint.getFinalizationRequested());
        finalizationReason = checkpoint.getFinalizationReason();
    }

    public synchronized boolean isFinalizationRequested() {
        return finalizationRequested;
    }

    public synchronized String finalizationReason() {
        return Optional.ofNullable(finalizationReason).filter(value -> !value.isBlank())
                .orElse("运行预算已到边界或工具结果已经收敛");
    }

    public boolean hasWorkingCapacity() {
        Integer usedTokens = tokenUsage.snapshot().totalTokens();
        return modelCalls.get() < policy.getMaxModelCalls() - 1
                && Objects.requireNonNullElse(usedTokens, 0) < policy.getMaxTotalTokens();
    }

    public boolean hasFinalizationCapacity() {
        Integer usedTokens = tokenUsage.snapshot().totalTokens();
        return modelCalls.get() < policy.getMaxModelCalls()
                && Objects.requireNonNullElse(usedTokens, 0) < policy.getMaxFinalizationTokens();
    }

    public boolean shouldFinalize() {
        Integer usedTokens = tokenUsage.snapshot().totalTokens();
        return isFinalizationRequested() || modelCalls.get() >= policy.getMaxModelCalls() - 1
                || policy.getMaxTotalTokens() > 0
                && Objects.requireNonNullElse(usedTokens, 0) >= policy.getMaxTotalTokens();
    }

    public int remainingModelCalls() {
        return Math.max(policy.getMaxModelCalls() - modelCalls.get(), 0);
    }

    public int modelCalls() {
        return modelCalls.get();
    }

    public int toolCalls() {
        return toolCalls.get();
    }

    public TokenUsage tokenUsage() {
        return tokenUsage.snapshot();
    }

    public ExecutionPolicy policy() {
        return policy;
    }

    private void ensureTokenCapacity(int limit, String errorCode) {
        Integer used = tokenUsage.snapshot().totalTokens();
        if (Objects.requireNonNullElse(used, 0) >= limit) {
            throw new AgentRuntimeLimitException(errorCode, "Agent Token额度已达到上限");
        }
    }
}
