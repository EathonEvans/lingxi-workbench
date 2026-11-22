package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 将 AgentScope 原生任务提交与结果等待投影为可配对的运行观测事件。 */
final class AgentScopeCoordinationMiddleware implements MiddlewareBase {

    private static final String AGENT_SPAWN = "agent_spawn";
    private static final String WAIT_ASYNC_RESULTS = "wait_async_results";
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            AGENT_SPAWN, "任务提交",
            WAIT_ASYNC_RESULTS, "结果等待");

    private final ExecutionEvents events;

    AgentScopeCoordinationMiddleware(ExecutionEvents events) {
        this.events = Objects.requireNonNull(events, "Runtime事件发布器不能为空");
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.defer(() -> {
            Map<String, CoordinationCall> activeCalls = activeCalls(input);
            activeCalls.forEach((invocationId, call) -> events.coordinationStarted(
                    invocationId, call.operation(), call.displayName()));
            return next.apply(input)
                    .doOnNext(event -> complete(activeCalls, event))
                    .doOnError(ignored -> failOpen(activeCalls, "AGENTSCOPE_COORDINATION_FAILED"))
                    .doOnCancel(() -> failOpen(activeCalls, "AGENTSCOPE_COORDINATION_CANCELLED"))
                    .doOnComplete(() -> failOpen(activeCalls, "AGENTSCOPE_COORDINATION_RESULT_MISSING"));
        });
    }

    private static Map<String, CoordinationCall> activeCalls(ActingInput input) {
        Map<String, CoordinationCall> calls = new ConcurrentHashMap<>();
        for (ToolUseBlock toolCall : input.toolCalls()) {
            String displayName = DISPLAY_NAMES.get(toolCall.getName());
            if (Objects.isNull(displayName) || Objects.isNull(toolCall.getId()) || toolCall.getId().isBlank()) {
                continue;
            }
            calls.putIfAbsent(toolCall.getId(),
                    new CoordinationCall(toolCall.getName(), displayName, System.nanoTime()));
        }
        return calls;
    }

    private void complete(Map<String, CoordinationCall> activeCalls, AgentEvent event) {
        if (!(event instanceof ToolResultEndEvent result)) {
            return;
        }
        CoordinationCall call = activeCalls.remove(result.getToolCallId());
        if (Objects.isNull(call)) {
            return;
        }
        long durationMillis = elapsedMillis(call.startedAt());
        if (result.getState() == ToolResultState.SUCCESS) {
            events.coordinationCompleted(result.getToolCallId(), call.operation(), call.displayName(),
                    durationMillis);
            return;
        }
        events.coordinationFailed(result.getToolCallId(), call.operation(), call.displayName(), durationMillis,
                "AGENTSCOPE_COORDINATION_" + result.getState().name());
    }

    private void failOpen(Map<String, CoordinationCall> activeCalls, String code) {
        activeCalls.forEach((invocationId, call) -> {
            if (activeCalls.remove(invocationId, call)) {
                events.coordinationFailed(invocationId, call.operation(), call.displayName(),
                        elapsedMillis(call.startedAt()), code);
            }
        });
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * @param operation   AgentScope 原生操作编码
     * @param displayName 管理端可读名称
     * @param startedAt   单调时钟开始值
     */
    private record CoordinationCall(String operation, String displayName, long startedAt) {
    }
}
