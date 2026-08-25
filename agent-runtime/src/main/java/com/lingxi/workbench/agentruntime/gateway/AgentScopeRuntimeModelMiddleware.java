package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 将 AgentScope 模型事件投影为 Lingxi 的预算、用量和稳定审计事件。
 */
final class AgentScopeRuntimeModelMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        ExecutionState state = context.get(ExecutionState.class);
        if (state == null) {
            return Flux.error(new IllegalStateException("AgentScope调用缺少可信ExecutionState"));
        }
        return Flux.defer(() -> {
            boolean finalCall = state.isFinalizationRequested();
            int callNo = finalCall ? state.beforeFinalModelCall() : state.beforeModelCall();
            AtomicBoolean completed = new AtomicBoolean();
            return next.apply(input)
                    .doOnNext(event -> completeModelCall(state, callNo, finalCall, completed, event));
        });
    }

    private static void completeModelCall(ExecutionState state, int callNo, boolean finalCall,
                                          AtomicBoolean completed, AgentEvent event) {
        if (event instanceof ModelCallEndEvent modelCallEnd && completed.compareAndSet(false, true)) {
            state.completeModelCall(callNo, convertUsage(modelCallEnd.getUsage()), finalCall);
        }
    }

    private static TokenUsage convertUsage(ChatUsage usage) {
        if (usage == null) {
            return TokenUsage.empty();
        }
        int inputTokens = usage.getInputTokens();
        int outputTokens = usage.getOutputTokens();
        int totalTokens = usage.getTotalTokens();
        if (inputTokens <= 0 && outputTokens <= 0 && totalTokens <= 0) {
            return TokenUsage.empty();
        }
        return TokenUsage.of(inputTokens, outputTokens, totalTokens);
    }
}
