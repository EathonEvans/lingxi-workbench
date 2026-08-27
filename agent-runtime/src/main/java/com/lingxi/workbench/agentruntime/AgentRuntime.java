package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.gateway.AgentScopeSpecialistStreamingGateway;
import com.lingxi.workbench.agentruntime.gateway.AgentScopeSupervisorGateway;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.context.AgentScopeConversationStateFactory;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeException;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionInterruption;
import com.lingxi.workbench.agentruntime.model.ExecutionOutput;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillCatalog;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Lingxi Agent Runtime 唯一运行门面。
 *
 * <p>AgentScope 负责消息循环、Agent 生命周期、Toolkit、Permission 和任务协调；Lingxi 仅把
 * 发布版本预算、Tool 授权、事件投影、输出与 HITL 中断作为类型化组件放入 RuntimeContext。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private static final Duration CANCELLATION_POLL_INTERVAL = Duration.ofMillis(100);

    private final AgentScopeSpecialistStreamingGateway agentScopeSpecialistStreamingGateway;
    private final AgentScopeSupervisorGateway agentScopeSupervisorGateway;
    private final AgentStateStore agentStateStore;


    /**
     * 执行一次已完成版本和可信上下文准备的 Agent 调用。
     */
    public AgentRunResult run(AgentRunRequest request) {
        requireRequest(request);
        RuntimeContext runtimeContext = runtimeContext(request, null);
        ExecutionBudget budget = require(runtimeContext, ExecutionBudget.class);
        ExecutionEvents events = require(runtimeContext, ExecutionEvents.class);
        events.start();
        if (request.isFinalizationOnly()) {
            requestFinalization(budget, events, "Application已进入仅收尾阶段");
        }
        if (request.getRole() == AgentRole.SUPERVISOR) {
            return executeSupervisor(request, runtimeContext);
        }
        AgentScopePublishedSkillCatalog skillCatalog = AgentScopePublishedSkillCatalog.from(request.getSkillCatalog(),
                !request.isFinalizationOnly());

        Flux<ChatStreamChunk> responseStream = agentScopeSpecialistStreamingGateway.stream(
                request, runtimeContext, skillCatalog);
        return execute(runtimeContext, responseStream);
    }

    /**
     * 恢复 AgentScope 持久状态，禁止重新生成已获批准的 Tool 调用。
     */
    public AgentRunResult resume(AgentResumeRequest resumeRequest) {
        requireResumeRequest(resumeRequest);
        AgentRunRequest request = resumeRequest.runRequest();
        AgentScopeCheckpoint checkpoint = resumeRequest.checkpoint();
        validateCheckpoint(request, checkpoint);
        RuntimeContext runtimeContext = runtimeContext(request, checkpoint);
        AgentScopePublishedSkillCatalog skillCatalog = AgentScopePublishedSkillCatalog.from(request.getSkillCatalog(), !request.isFinalizationOnly());
        Flux<ChatStreamChunk> stream = agentScopeSpecialistStreamingGateway.resume(request, runtimeContext,
                skillCatalog, checkpoint, resumeRequest.interactionId(),
                resumeRequest.fingerprint());
        return execute(runtimeContext, stream);
    }

    private AgentRunResult execute(RuntimeContext context, Flux<ChatStreamChunk> responseStream) {
        ExecutionBudget budget = require(context, ExecutionBudget.class);
        ExecutionEvents events = require(context, ExecutionEvents.class);
        ExecutionOutput output = require(context, ExecutionOutput.class);
        ExecutionInterruption interruption = require(context, ExecutionInterruption.class);

        Mono<Long> cancellation = Flux.interval(CANCELLATION_POLL_INTERVAL)
                .filter(ignored -> events.isCancelled())
                .next();

        try {
            responseStream
                    .doOnNext(chunk -> acceptChunk(output, events, chunk))
                    .takeUntilOther(cancellation)
                    .then()
                    .block();
            return finish(budget, events, output, interruption);
        } catch (RuntimeException exception) {
            if (events.isCancelled()) {
                events.cancel();
                throw new AgentRunCancelledException();
            }
            events.fail(exception);
            throw exception;
        }
    }

    /**
     * Supervisor 与 Specialist 共用取消和终态事件出口。
     */
    private AgentRunResult executeSupervisor(AgentRunRequest request, RuntimeContext runtimeContext) {
        ExecutionEvents events = require(runtimeContext, ExecutionEvents.class);
        try {
            AgentRunResult result = agentScopeSupervisorGateway.execute(request, runtimeContext);
            if (result instanceof AgentRunResult.Interrupted) {
                return result;
            }
            events.complete();
            return result;
        } catch (AgentRunCancelledException | java.util.concurrent.CancellationException exception) {
            events.cancel();
            throw exception;
        } catch (RuntimeException exception) {
            events.fail(exception);
            throw exception;
        }
    }

    private static void acceptChunk(ExecutionOutput output, ExecutionEvents events, ChatStreamChunk chunk) {
        output.append(chunk.content());
        events.acceptText(chunk);
    }

    private static AgentRunResult finish(ExecutionBudget budget, ExecutionEvents events, ExecutionOutput output,
                                         ExecutionInterruption interruption) {
        if (interruption.isPresent()) {
            return new AgentRunResult.Interrupted(interruption.get(), budget.tokenUsage(),
                    events.lastSequenceNo());
        }
        if (events.isCancelled()) {
            events.cancel();
            throw new AgentRunCancelledException();
        }
        if (output.content().isBlank()) {
            AgentRuntimeException exception = new AgentRuntimeException(
                    "AGENT_EMPTY_RESPONSE", "Agent未返回可用结果", false, null);
            events.fail(exception);
            throw exception;
        }
        events.complete();
        return new AgentRunResult.Completed(output.content(), budget.tokenUsage(), events.lastSequenceNo());
    }

    private static void requireRequest(AgentRunRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.getModelCommand())
                || Objects.isNull(request.getUserId()) || request.getUserId().isBlank()
                || Objects.isNull(request.getSessionId()) || request.getSessionId().isBlank()
                || Objects.isNull(request.getRole()) || Objects.isNull(request.getPolicy())
                || Objects.isNull(request.getEventSink())
                || (request.getRole() == AgentRole.SUPERVISOR && request.getSupervisorConfig() == null)
                || (request.getRole() == AgentRole.SPECIALIST && request.getSupervisorConfig() != null)) {
            throw new IllegalArgumentException("Agent运行请求不完整");
        }
    }

    private static void requireResumeRequest(AgentResumeRequest resumeRequest) {
        if (resumeRequest == null || resumeRequest.runRequest() == null) {
            throw new IllegalArgumentException("Agent恢复请求或Checkpoint版本不合法");
        }
        requireRequest(resumeRequest.runRequest());
        if (resumeRequest.checkpoint() == null || resumeRequest.checkpoint().getRunId() == null
                || resumeRequest.checkpoint().getVersion() == null
                || resumeRequest.checkpoint().getVersion() != AgentScopeCheckpoint.CURRENT_VERSION
                || resumeRequest.checkpoint().getAgentSessionId() == null
                || resumeRequest.checkpoint().getAgentSessionId().isBlank()
                || resumeRequest.interactionId() == null || resumeRequest.fingerprint() == null
                || !Objects.equals(resumeRequest.runRequest().getSessionId(),
                resumeRequest.checkpoint().getAgentSessionId())
                || !Objects.equals(resumeRequest.fingerprint(), resumeRequest.checkpoint().getFingerprint())) {
            throw new IllegalArgumentException("Agent恢复请求或Checkpoint版本不合法");
        }
    }

    /**
     * AgentScope 运行对象只在 AgentRuntime 内产生，Application 不再组装或传递框架 Context。
     */
    private RuntimeContext runtimeContext(AgentRunRequest request, AgentScopeCheckpoint checkpoint) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .agentState(AgentScopeConversationStateFactory.create(
                        request.getModelCommand(), request.getUserId(), request.getSessionId()))
                .build();
        context.put(AgentStateStore.class, agentStateStore);
        int modelCalls;
        int toolCalls;
        TokenUsage usage;
        long sequence;
        if (Objects.isNull(checkpoint)) {
            modelCalls = request.getInitialModelCalls();
            toolCalls = 0;
            usage = request.getInitialUsage();
            sequence = request.getInitialSequenceNo();
        } else {
            modelCalls = checkpoint.getModelCalls();
            toolCalls = checkpoint.getToolCalls();
            usage = checkpoint.getTokenUsage();
            sequence = Math.max(request.getInitialSequenceNo(),
                    Objects.requireNonNullElse(checkpoint.getLastSequenceNo(), 0L));
        }
        ExecutionBudget budget = new ExecutionBudget(request.getPolicy(), modelCalls, toolCalls, usage);
        ExecutionTools tools = new ExecutionTools(request.getModelCommand().toolRuntimeConfig().orElse(null),
                request.getAuthorizedToolCodes(), request.getPolicy());
        ExecutionOutput output = new ExecutionOutput();
        if (Objects.nonNull(checkpoint)) {
            budget.restoreFinalization(checkpoint);
            tools.restore(checkpoint);
            output.restore(checkpoint.getOutput());
        }
        context.put(ExecutionBudget.class, budget);
        context.put(ExecutionTools.class, tools);
        context.put(ExecutionEvents.class, new ExecutionEvents(request.getRunId(), request.getAgentId(),
                request.getAgentVersionId(), request.getScope(), request.getEventSink(), sequence));
        context.put(ExecutionOutput.class, output);
        context.put(ExecutionInterruption.class, new ExecutionInterruption());
        return context;
    }

    private static void validateCheckpoint(AgentRunRequest request, AgentScopeCheckpoint checkpoint) {
        java.util.Set<String> authorized = java.util.Set.copyOf(
                Objects.requireNonNullElse(checkpoint.getAuthorizedToolCodes(), java.util.List.of()));
        if (!Objects.equals(request.getRunId(), checkpoint.getRunId())
                || !Objects.equals(request.getAgentId(), checkpoint.getAgentId())
                || !Objects.equals(request.getAgentVersionId(), checkpoint.getAgentVersionId())
                || !authorized.equals(java.util.Set.copyOf(request.getAuthorizedToolCodes()))
                || !Objects.equals(checkpoint.getMaxModelCalls(), request.getPolicy().getMaxModelCalls())
                || !Objects.equals(checkpoint.getMaxToolCalls(), request.getPolicy().getMaxToolCalls())
                || !Objects.equals(checkpoint.getMaxTotalTokens(), request.getPolicy().getMaxTotalTokens())
                || !Objects.equals(checkpoint.getMaxFinalizationTokens(),
                request.getPolicy().getMaxFinalizationTokens())) {
            throw new IllegalArgumentException("Checkpoint运行身份、Tool授权或执行策略已经漂移");
        }
    }

    private static void requestFinalization(ExecutionBudget budget, ExecutionEvents events, String reason) {
        if (budget.requestFinalization(reason)) {
            events.finalizing(budget.finalizationReason());
        }
    }

    private static <T> T require(RuntimeContext context, Class<T> type) {
        T value = context.get(type);
        if (value == null) {
            throw new IllegalStateException("AgentScope调用缺少可信" + type.getSimpleName());
        }
        return value;
    }
}
