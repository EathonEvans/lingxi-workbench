package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.gateway.SpringAiStreamingChatGateway;
import com.lingxi.workbench.agentruntime.hook.SkillHook;
import com.lingxi.workbench.agentruntime.checkpoint.ExecutionStateSnapshot;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.model.AgentExecutionDeadlineExceededException;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lingxi Agent Runtime 唯一运行门面。
 *
 * <p>生命周期、ExecutionState、事件序号、Skill Hook、Spring AI Advisor 和 Tool 迭代
 * 均由该门面唯一管理；Application 只提交锁定版本与可信上下文。</p>
 */
@Component
public class AgentRuntime {

    private static final Duration CANCELLATION_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration BLOCK_GRACE = Duration.ofSeconds(2);

    private final SpringAiStreamingChatGateway streamingChatGateway;
    private final SkillHook skillHook;

    public AgentRuntime(SpringAiStreamingChatGateway streamingChatGateway) {
        this.streamingChatGateway = streamingChatGateway;
        this.skillHook = new SkillHook();
    }

    /**
     * 执行一次已完成版本和可信上下文准备的 Agent 调用。
     */
    public AgentRunResult run(AgentRunRequest request) {
        requireRequest(request);
        ExecutionState state = ExecutionState.builder()
                .runId(request.getRunId())
                .agentId(request.getAgentId())
                .agentVersionId(request.getAgentVersionId())
                .policy(request.getPolicy())
                .eventSink(request.getEventSink())
                .toolRuntimeConfig(request.getModelCommand().toolRuntimeConfig().orElse(null))
                .authorizedToolCodes(request.getAuthorizedToolCodes())
                .initialSequenceNo(request.getInitialSequenceNo())
                .initialModelCalls(request.getInitialModelCalls())
                .initialToolCalls(0)
                .initialUsage(request.getInitialUsage())
                .checkpointContext(request.getCheckpointContext())
                .checkpointStore(request.getCheckpointStore())
                .build();
        state.start();
        skillHook.beforeAgent(state, request.getSkillActivations());
        if (request.isFinalizationOnly()) {
            state.requestFinalization("Application已进入仅收尾阶段");
        }

        return execute(request, state, streamingChatGateway.stream(request.getModelCommand(), state));
    }

    /** 恢复持久Checkpoint，禁止重新请求模型生成待批准ToolCall。 */
    public AgentRunResult resume(AgentResumeRequest resumeRequest) {
        requireResumeRequest(resumeRequest);
        AgentRunRequest request = resumeRequest.runRequest();
        RuntimeCheckpoint checkpoint = resumeRequest.checkpoint();
        ExecutionStateSnapshot snapshot = checkpoint.getState();
        long activeElapsedMillis = Objects.requireNonNullElse(snapshot.getActiveElapsedMillis(), 0L);
        long initialSequenceNo = Math.max(request.getInitialSequenceNo(),
                Objects.requireNonNullElse(snapshot.getLastSequenceNo(), 0L));
        ExecutionState state = ExecutionState.builder()
                .runId(request.getRunId())
                .agentId(request.getAgentId())
                .agentVersionId(request.getAgentVersionId())
                .policy(request.getPolicy())
                .eventSink(request.getEventSink())
                .toolRuntimeConfig(request.getModelCommand().toolRuntimeConfig().orElse(null))
                .authorizedToolCodes(request.getAuthorizedToolCodes())
                .initialSequenceNo(initialSequenceNo)
                .initialModelCalls(snapshot.getModelCalls())
                .initialToolCalls(snapshot.getToolCalls())
                .initialUsage(snapshot.getTokenUsage())
                .startedAt(Instant.now().minusMillis(activeElapsedMillis))
                .checkpointContext(request.getCheckpointContext())
                .checkpointStore(request.getCheckpointStore())
                .build();
        state.restore(snapshot, request.getSkillActivations());
        Flux<ChatStreamChunk> stream = streamingChatGateway.resume(request.getModelCommand(), state,
                checkpoint, resumeRequest.interactionId(), resumeRequest.fingerprint());
        return execute(request, state, stream);
    }

    private AgentRunResult execute(AgentRunRequest request, ExecutionState state,
                                   Flux<ChatStreamChunk> responseStream) {

        AtomicReference<StopReason> stopReason = new AtomicReference<>();
        Duration remaining = Duration.between(Instant.now(),
                state.getStartedAt().plus(request.getPolicy().getTimeout()));
        Duration timeoutDelay = remaining.isNegative() || remaining.isZero()
                ? Duration.ofMillis(1) : remaining;
        Mono<StopReason> timeout = Mono.delay(timeoutDelay)
                .flatMap(ignored -> state.canUseActiveOutputGrace()
                        ? Mono.delay(request.getPolicy().getActiveOutputGrace())
                        .map(graceIgnored -> StopReason.TIMEOUT)
                        : Mono.just(StopReason.TIMEOUT));
        Mono<StopReason> cancellation = Flux.interval(CANCELLATION_POLL_INTERVAL)
                .filter(ignored -> state.isCancelled())
                .next()
                .map(ignored -> StopReason.CANCELLED);
        Mono<StopReason> stop = Mono.firstWithSignal(timeout, cancellation)
                .doOnNext(reason -> stopReason.compareAndSet(null, reason));

        try {
            responseStream
                    .doOnNext(chunk -> acceptChunk(state, request, chunk))
                    .takeUntilOther(stop)
                    .then()
                    .block(request.getPolicy().getTimeout()
                            .plus(request.getPolicy().getActiveOutputGrace())
                            .plus(BLOCK_GRACE));
            return finish(state, stopReason.get());
        } catch (RuntimeException exception) {
            if (state.isCancelled()) {
                state.cancel();
                throw new AgentRunCancelledException();
            }
            state.fail(exception);
            throw exception;
        }
    }

    private static void acceptChunk(ExecutionState state, AgentRunRequest request, ChatStreamChunk chunk) {
        state.appendOutput(chunk.content());
        request.getEventSink().onText(chunk);
    }

    private static AgentRunResult finish(ExecutionState state, StopReason stopReason) {
        if (state.getStatus() == com.lingxi.workbench.agentruntime.model.ExecutionStatus.INTERRUPTED) {
            return new AgentRunResult.Interrupted(state.interruption(), state.tokenUsage(),
                    state.lastSequenceNo());
        }
        if (stopReason == StopReason.CANCELLED || state.isCancelled()) {
            state.cancel();
            throw new AgentRunCancelledException();
        }
        if (stopReason == StopReason.TIMEOUT) {
            if (state.output().isBlank()) {
                state.fail(new AgentExecutionDeadlineExceededException());
                throw new AgentExecutionDeadlineExceededException();
            }
            state.completePartial("Agent运行达到时间上限，保留已生成的可用结果");
            return new AgentRunResult.Partial(state.output(), state.tokenUsage(), state.lastSequenceNo());
        }
        if (state.output().isBlank()) {
            IllegalStateException exception = new IllegalStateException("Agent模型未返回可用文本");
            state.fail(exception);
            throw exception;
        }
        state.complete();
        return new AgentRunResult.Completed(state.output(), state.tokenUsage(), state.lastSequenceNo());
    }

    private static void requireRequest(AgentRunRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.getModelCommand())
                || Objects.isNull(request.getPolicy()) || Objects.isNull(request.getEventSink())) {
            throw new IllegalArgumentException("Agent运行请求不完整");
        }
    }

    private static void requireResumeRequest(AgentResumeRequest resumeRequest) {
        if (resumeRequest == null || resumeRequest.runRequest() == null
                || resumeRequest.checkpoint() == null || resumeRequest.checkpoint().getState() == null
                || resumeRequest.checkpoint().getVersion() == null
                || resumeRequest.checkpoint().getVersion() != RuntimeCheckpoint.CURRENT_VERSION
                || resumeRequest.interactionId() == null || resumeRequest.fingerprint() == null
                || !Objects.equals(resumeRequest.fingerprint(), resumeRequest.checkpoint().getFingerprint())) {
            throw new IllegalArgumentException("Agent恢复请求或Checkpoint版本不合法");
        }
        requireRequest(resumeRequest.runRequest());
    }

    private enum StopReason {
        TIMEOUT,
        CANCELLED
    }
}
