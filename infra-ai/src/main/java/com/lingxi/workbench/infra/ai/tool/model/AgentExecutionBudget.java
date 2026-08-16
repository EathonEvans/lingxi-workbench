package com.lingxi.workbench.infra.ai.tool.model;

/**
 * Agent循环向上层共享执行预算申请额度的稳定边界。
 *
 * <p>infra-ai只负责在真实调用前申请额度，预算归属和失败语义由调用方决定。</p>
 */
public interface AgentExecutionBudget {

    /** 在发起一轮模型请求前占用一次额度。 */
    void reserveModelCall();

    /**
     * 为禁用工具的最终回答占用一次额度。
     *
     * <p>默认实现沿用普通模型额度；Application运行时会为该调用保留最后一次额度。</p>
     */
    default void reserveFinalModelCall() {
        reserveModelCall();
    }

    /** 在执行模型请求的一批工具前占用对应额度。 */
    void reserveToolCalls(int calls);

    /** 是否应停止继续探索并进入禁用工具的最终回答。 */
    default boolean shouldFinalize() {
        return false;
    }

    static AgentExecutionBudget noop() {
        return NoopAgentExecutionBudget.INSTANCE;
    }
}

final class NoopAgentExecutionBudget implements AgentExecutionBudget {

    static final NoopAgentExecutionBudget INSTANCE = new NoopAgentExecutionBudget();

    private NoopAgentExecutionBudget() {
    }

    @Override
    public void reserveModelCall() {
    }

    @Override
    public void reserveToolCalls(int calls) {
    }
}
