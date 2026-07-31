package com.lingxi.workbench.infra.ai.chat.model.bo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次 Agent 执行期间的线程安全 Token 用量累加器。
 *
 * <p>累加器只管理可变生命周期，对外始终返回不可变 {@link TokenUsage} 快照。</p>
 */
public final class TokenUsageAccumulator {

    private final AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.empty());

    /** 累加一次模型调用返回的 Token 用量。 */
    public void add(TokenUsage increment) {
        if (Objects.isNull(increment) || !increment.hasUsage()) {
            return;
        }
        usage.updateAndGet(current -> current.plus(increment));
    }

    /** 使用一个确定快照覆盖当前用量。 */
    public void initialize(TokenUsage initialUsage) {
        usage.set(Objects.requireNonNullElseGet(initialUsage, TokenUsage::empty));
    }

    /** 返回当前不可变用量快照。 */
    public TokenUsage snapshot() {
        return usage.get();
    }

    /** 清空所有已累计用量。 */
    public void reset() {
        usage.set(TokenUsage.empty());
    }
}
