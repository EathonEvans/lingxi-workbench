package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** 单次 Application 执行各分支共享的模型、Tool、委派和 Token 预算事实。 */
public final class ApplicationExecutionBudget {

    private final ApplicationRuntimePolicy runtimePolicy;
    private final TokenUsageAccumulator tokenUsageAccumulator;
    private final AtomicInteger modelCallCounter;
    private final AtomicInteger toolCallCounter;
    private final AtomicInteger delegationCounter;

    /** 按已冻结的运行策略创建新账本。 */
    public static ApplicationExecutionBudget fromRuntimePolicy(ApplicationRuntimePolicy runtimePolicy) {
        return new ApplicationExecutionBudget(runtimePolicy, null, null, null, null);
    }

    public ApplicationExecutionBudget(ApplicationRuntimePolicy runtimePolicy,
                                      TokenUsageAccumulator tokenUsageAccumulator,
                                      AtomicInteger modelCallCounter,
                                      AtomicInteger toolCallCounter,
                                      AtomicInteger delegationCounter) {
        this.runtimePolicy = Objects.requireNonNull(runtimePolicy, "Application运行策略不能为空");
        this.tokenUsageAccumulator = Objects.requireNonNullElseGet(tokenUsageAccumulator, TokenUsageAccumulator::new);
        this.modelCallCounter = Objects.requireNonNullElseGet(modelCallCounter, AtomicInteger::new);
        this.toolCallCounter = Objects.requireNonNullElseGet(toolCallCounter, AtomicInteger::new);
        this.delegationCounter = Objects.requireNonNullElseGet(delegationCounter, AtomicInteger::new);
    }

    public ApplicationRuntimePolicy runtimePolicy() {
        return runtimePolicy;
    }

    public void reserveModelCall() {
        reserveCalls(modelCallCounter, 1, runtimePolicy.getMaxModelCalls(), "Application单轮模型调用次数已达到上限");
    }

    public void reserveFinalModelCall() {
        reserveCalls(modelCallCounter, 1, runtimePolicy.getMaxModelCalls(), "Application单轮模型调用次数已达到上限");
    }

    public void reserveToolCalls(int calls) {
        if (calls < 1) {
            throw new IllegalArgumentException("Tool调用次数必须大于0");
        }
        reserveCalls(toolCallCounter, calls, runtimePolicy.getMaxToolCalls(), "Application单轮Tool调用次数已达到上限");
    }

    public void reserveDelegation() {
        reserveCalls(delegationCounter, 1, runtimePolicy.getMaxDelegations(), "Application单轮Agent委派次数已达到上限");
    }

    public int remainingModelCalls() {
        return Math.max(runtimePolicy.getMaxModelCalls() - modelCallCounter.get(), 0);
    }

    public int remainingToolCalls() {
        return Math.max(runtimePolicy.getMaxToolCalls() - toolCallCounter.get(), 0);
    }

    public int remainingDelegations() {
        return Math.max(runtimePolicy.getMaxDelegations() - delegationCounter.get(), 0);
    }

    public int usedModelCalls() {
        return modelCallCounter.get();
    }

    public int usedToolCalls() {
        return toolCallCounter.get();
    }

    public boolean shouldFinalize() {
        return hasReachedTokenSoftLimit() || remainingModelCalls() <= 1;
    }

    public void recordUsage(TokenUsage usage) {
        tokenUsageAccumulator.add(usage);
    }

    public int remainingTokenBudget() {
        int remaining = runtimePolicy.getTotalTokenBudget() - usedTokenCount();
        if (remaining < 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application Token工作额度已用尽，剩余额度仅用于最终回答");
        }
        return remaining;
    }

    public int remainingFinalizationTokenBudget() {
        int remaining = runtimePolicy.effectiveTokenHardBudget() - usedTokenCount();
        if (remaining < 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application单轮执行超过Token硬上限");
        }
        return remaining;
    }

    public boolean hasReachedTokenSoftLimit() {
        return usedTokenCount() >= runtimePolicy.getTotalTokenBudget();
    }

    public boolean hasFinalizationCapacity() {
        return remainingModelCalls() > 0 && usedTokenCount() < runtimePolicy.effectiveTokenHardBudget();
    }

    public TokenUsage tokenUsage() {
        return tokenUsageAccumulator.snapshot();
    }

    private static void reserveCalls(AtomicInteger counter, int calls, int limit, String message) {
        while (true) {
            int current = counter.get();
            if (calls > limit - current) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, message);
            }
            if (counter.compareAndSet(current, current + calls)) {
                return;
            }
        }
    }

    private int usedTokenCount() {
        Integer totalTokens = tokenUsageAccumulator.snapshot().totalTokens();
        return Objects.requireNonNullElse(totalTokens, 0);
    }
}
