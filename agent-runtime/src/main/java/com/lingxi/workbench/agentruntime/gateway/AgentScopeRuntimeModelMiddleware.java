package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 将 AgentScope 模型事件投影为 Lingxi 的预算、用量和稳定审计事件。
 */
final class AgentScopeRuntimeModelMiddleware implements MiddlewareBase {

    private final Long modelInstanceId;
    private final String modelCode;

    AgentScopeRuntimeModelMiddleware(Long modelInstanceId, String modelCode) {
        this.modelInstanceId = modelInstanceId;
        this.modelCode = modelCode;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        ExecutionBudget budget = context.get(ExecutionBudget.class);
        ExecutionTools tools = context.get(ExecutionTools.class);
        ExecutionEvents events = context.get(ExecutionEvents.class);
        if (budget == null || tools == null || events == null) {
            return Flux.error(new IllegalStateException("AgentScope调用缺少可信预算、Tool或事件上下文"));
        }
        return Flux.defer(() -> {
            if (events.isCancelled()) {
                return Flux.error(new com.lingxi.workbench.agentruntime.model.AgentRunCancelledException());
            }
            boolean finalCall = budget.isFinalizationRequested();
            int callNo = finalCall ? budget.reserveFinalModelCall() : budget.reserveModelCall();
            tools.startModelRound();
            events.modelStarted(callNo, modelInstanceId, modelCode, finalCall);
            AtomicBoolean completed = new AtomicBoolean();
            AtomicLong firstResponseMillis = new AtomicLong(-1L);
            long startedAt = System.nanoTime();
            return next.apply(input)
                    .doOnNext(event -> {
                        recordFirstResponse(firstResponseMillis, startedAt, event);
                        completeModelCall(budget, events, callNo, finalCall, completed, firstResponseMillis, event);
                    });
        });
    }

    private void completeModelCall(ExecutionBudget budget, ExecutionEvents events, int callNo, boolean finalCall,
                                   AtomicBoolean completed, AtomicLong firstResponseMillis, AgentEvent event) {
        if (event instanceof ModelCallEndEvent modelCallEnd && completed.compareAndSet(false, true)) {
            TokenUsage usage = convertUsage(modelCallEnd.getUsage());
            budget.completeModelCall(usage);
            long responseMillis = firstResponseMillis.get();
            events.modelCompleted(callNo, modelInstanceId, modelCode, usage, finalCall,
                    responseMillis < 0 ? null : responseMillis);
        }
    }

    private static void recordFirstResponse(AtomicLong firstResponseMillis, long startedAt, AgentEvent event) {
        if (firstResponseMillis.get() >= 0 || !isResponseContent(event)) {
            return;
        }
        firstResponseMillis.compareAndSet(-1L, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static boolean isResponseContent(AgentEvent event) {
        return switch (event.getType()) {
            case THINKING_BLOCK_START, TEXT_BLOCK_START, TOOL_CALL_START, DATA_BLOCK_START -> true;
            default -> false;
        };
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
