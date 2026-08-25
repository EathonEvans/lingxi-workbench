package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.gateway.AgentScopeSpecialistStreamingGateway;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillCatalog;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import io.agentscope.core.state.AgentStateStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Lingxi Agent Runtime 唯一运行门面。
 *
 * <p>生命周期、ExecutionState、事件序号和发布 Skill 目录均由该门面唯一管理；所有 Specialist
 * 统一由 AgentScope ReActAgent、Toolkit 和 Permission Engine 执行。</p>
 */
@Component
public class AgentRuntime {

    private static final Duration CANCELLATION_POLL_INTERVAL = Duration.ofMillis(100);

    private final AgentScopeSpecialistStreamingGateway agentScopeSpecialistStreamingGateway;

    public AgentRuntime(AgentScopeSpecialistStreamingGateway agentScopeSpecialistStreamingGateway) {
        this.agentScopeSpecialistStreamingGateway = agentScopeSpecialistStreamingGateway;
    }

    /**
     * 执行一次已完成版本和可信上下文准备的 Agent 调用。
     */
    public AgentRunResult run(AgentRunRequest request) {
        requireRequest(request);
        request.getRuntimeContext().put(AgentStateStore.class, request.getAgentStateStore());
        ExecutionState state = ExecutionState.builder()
                .runId(request.getRunId())
                .agentId(request.getAgentId())
                .agentVersionId(request.getAgentVersionId())
                .scope(request.getScope())
                .policy(request.getPolicy())
                .eventSink(request.getEventSink())
                .toolRuntimeConfig(request.getModelCommand().toolRuntimeConfig().orElse(null))
                .authorizedToolCodes(request.getAuthorizedToolCodes())
                .initialSequenceNo(request.getInitialSequenceNo())
                .initialModelCalls(request.getInitialModelCalls())
                .initialToolCalls(0)
                .initialUsage(request.getInitialUsage())
                .checkpointSeed(request.getCheckpointSeed())
                .checkpointStore(request.getCheckpointStore())
                .build();
        state.start();
        if (request.isFinalizationOnly()) {
            state.requestFinalization("Application已进入仅收尾阶段");
        }
        AgentScopePublishedSkillCatalog skillCatalog = AgentScopePublishedSkillCatalog.from(request.getSkillCatalog(),
                !request.isFinalizationOnly());

        Flux<ChatStreamChunk> responseStream = agentScopeSpecialistStreamingGateway.stream(
                request.getModelCommand(), state, request.getRuntimeContext(), skillCatalog);
        return execute(request, state, responseStream);
    }

    /** 恢复 AgentScope 持久状态，禁止重新生成已获批准的 Tool 调用。 */
    public AgentRunResult resume(AgentResumeRequest resumeRequest) {
        requireResumeRequest(resumeRequest);
        AgentRunRequest request = resumeRequest.runRequest();
        request.getRuntimeContext().put(AgentStateStore.class, request.getAgentStateStore());
        AgentScopeCheckpoint checkpoint = resumeRequest.checkpoint();
        long initialSequenceNo = Math.max(request.getInitialSequenceNo(),
                Objects.requireNonNullElse(checkpoint.getLastSequenceNo(), 0L));
        ExecutionState state = ExecutionState.builder()
                .runId(request.getRunId())
                .agentId(request.getAgentId())
                .agentVersionId(request.getAgentVersionId())
                .scope(request.getScope())
                .policy(request.getPolicy())
                .eventSink(request.getEventSink())
                .toolRuntimeConfig(request.getModelCommand().toolRuntimeConfig().orElse(null))
                .authorizedToolCodes(request.getAuthorizedToolCodes())
                .initialSequenceNo(initialSequenceNo)
                .initialModelCalls(checkpoint.getModelCalls())
                .initialToolCalls(checkpoint.getToolCalls())
                .initialUsage(checkpoint.getTokenUsage())
                .checkpointSeed(request.getCheckpointSeed())
                .checkpointStore(request.getCheckpointStore())
                .build();
        state.restore(checkpoint);
        AgentScopePublishedSkillCatalog skillCatalog = AgentScopePublishedSkillCatalog.from(request.getSkillCatalog(),
                !request.isFinalizationOnly());
        Flux<ChatStreamChunk> stream = agentScopeSpecialistStreamingGateway.resume(request.getModelCommand(), state,
                request.getRuntimeContext(), skillCatalog, checkpoint, resumeRequest.interactionId(),
                resumeRequest.fingerprint());
        return execute(request, state, stream);
    }

    private AgentRunResult execute(AgentRunRequest request, ExecutionState state,
                                   Flux<ChatStreamChunk> responseStream) {

        Mono<Long> cancellation = Flux.interval(CANCELLATION_POLL_INTERVAL)
                .filter(ignored -> state.isCancelled())
                .next();

        try {
            responseStream
                    .doOnNext(chunk -> acceptChunk(state, request, chunk))
                    .takeUntilOther(cancellation)
                    .then()
                    .block();
            return finish(state);
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

    private static AgentRunResult finish(ExecutionState state) {
        if (state.getStatus() == com.lingxi.workbench.agentruntime.model.ExecutionStatus.INTERRUPTED) {
            return new AgentRunResult.Interrupted(state.interruption(), state.tokenUsage(),
                    state.lastSequenceNo());
        }
        if (state.isCancelled()) {
            state.cancel();
            throw new AgentRunCancelledException();
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
                || Objects.isNull(request.getRuntimeContext()) || Objects.isNull(request.getPolicy())
                || Objects.isNull(request.getEventSink()) || Objects.isNull(request.getAgentStateStore())) {
            throw new IllegalArgumentException("Agent运行请求不完整");
        }
    }

    private static void requireResumeRequest(AgentResumeRequest resumeRequest) {
        if (resumeRequest == null || resumeRequest.runRequest() == null
                || resumeRequest.checkpoint() == null || resumeRequest.checkpoint().getRunId() == null
                || resumeRequest.checkpoint().getVersion() == null
                || resumeRequest.checkpoint().getVersion() != AgentScopeCheckpoint.CURRENT_VERSION
                || resumeRequest.interactionId() == null || resumeRequest.fingerprint() == null
                || !Objects.equals(resumeRequest.fingerprint(), resumeRequest.checkpoint().getFingerprint())) {
            throw new IllegalArgumentException("Agent恢复请求或Checkpoint版本不合法");
        }
        requireRequest(resumeRequest.runRequest());
    }
}
