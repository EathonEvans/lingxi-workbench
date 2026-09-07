package com.lingxi.workbench.agentruntime.event;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeException;
import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimeClarification;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import io.agentscope.core.state.Task;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 将 AgentScope 已发生的运行事实投影为 Lingxi 稳定事件协议。 */
public final class ExecutionEvents {

    private final String runId;
    private final Long agentId;
    private final Long agentVersionId;
    private final ExecutionScope scope;
    private final RuntimeEventSink sink;
    private final AtomicLong sequence;
    private final AtomicBoolean terminal = new AtomicBoolean();

    public ExecutionEvents(String runId, Long agentId, Long agentVersionId, ExecutionScope scope,
                           RuntimeEventSink sink, long initialSequenceNo) {
        if (runId == null || runId.isBlank() || agentId == null || agentVersionId == null) {
            throw new IllegalArgumentException("Agent执行身份不能为空");
        }
        this.runId = runId;
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.scope = Objects.requireNonNullElse(scope, ExecutionScope.RUN);
        this.sink = Objects.requireNonNull(sink, "Runtime事件接收器不能为空");
        this.sequence = new AtomicLong(initialSequenceNo);
    }

    public void start() {
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_STARTED, ExecutionStage.RUNTIME,
                    new ExecutionEventPayload.Run("Agent运行已开始"));
        }
        publish(ExecutionEventType.AGENT_STARTED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已开始"));
    }

    public void planning(int planNo) {
        ExecutionEventType type = planNo == 1 ? ExecutionEventType.PLANNING : ExecutionEventType.REPLANNING;
        publish(type, ExecutionStage.PLAN, new ExecutionEventPayload.Plan(planNo, List.of(),
                planNo == 1 ? "正在规划任务" : "正在重新规划任务"));
    }

    public void tasksCreated(int revision, List<Task> tasks) {
        List<Task> snapshot = List.copyOf(Objects.requireNonNullElse(tasks, List.of()));
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("AgentScope任务快照不能为空");
        }
        publish(ExecutionEventType.PLAN_CREATED, ExecutionStage.PLAN,
                new ExecutionEventPayload.Plan(revision, snapshot, "任务计划已更新"));
    }

    public void skillActivated(AgentSkillBindingSnapshot binding, SkillActivationReason reason) {
        requireSkillActivation(binding, reason);
        List<String> tools = List.copyOf(Objects.requireNonNullElse(binding.getEffectiveToolCodes(), List.of()));
        publish(ExecutionEventType.SKILL_ACTIVATED, ExecutionStage.SKILL,
                ExecutionEventPayload.Skill.builder().skillId(binding.getSkillId())
                        .skillVersionId(binding.getSkillVersionId()).versionNo(binding.getVersionNo())
                        .skillCode(binding.getSkill().getCode()).checksum(binding.getChecksum()).reason(reason)
                        .effectiveToolCodes(tools).message("Skill已激活：" + binding.getSkill().getName()).build());
    }

    public void modelStarted(int callNo, Long modelInstanceId, String modelCode, boolean finalCall) {
        publish(ExecutionEventType.MODEL_STARTED, ExecutionStage.MODEL,
                new ExecutionEventPayload.Model(callNo, modelInstanceId, modelCode, TokenUsage.empty(), finalCall, null,
                        finalCall ? "最终模型调用已开始" : "模型调用已开始"));
    }

    public void modelCompleted(int callNo, Long modelInstanceId, String modelCode, TokenUsage usage,
                               boolean finalCall, Long firstResponseMillis) {
        if (!terminal.get()) {
            publish(ExecutionEventType.MODEL_COMPLETED, ExecutionStage.MODEL,
                    new ExecutionEventPayload.Model(callNo, modelInstanceId, modelCode, usage, finalCall,
                            firstResponseMillis,
                            "模型调用已完成"));
        }
    }

    public void finalizing(String reason) {
        publish(ExecutionEventType.FINALIZING, ExecutionStage.AGENT, new ExecutionEventPayload.Run(reason));
    }

    public void stepStarted(int revision, AgentInvocationState invocation) {
        publishStep(ExecutionEventType.STEP_STARTED, revision, invocation, AgentInvocationStatus.RUNNING, null,
                "步骤已开始");
    }

    public void stepReused(int revision, AgentInvocationState invocation) {
        publishStep(ExecutionEventType.STEP_REUSED, revision, invocation, AgentInvocationStatus.REUSED, null,
                "已复用相同副作用步骤结果");
    }

    public void stepCompleted(int revision, AgentInvocationState invocation) {
        publishStep(ExecutionEventType.STEP_COMPLETED, revision, invocation, AgentInvocationStatus.COMPLETED, null,
                "步骤已完成");
    }

    public void stepFailed(int revision, AgentInvocationState invocation, String code, String message) {
        publishStep(ExecutionEventType.STEP_FAILED, revision, invocation, AgentInvocationStatus.FAILED, code, message);
    }

    public void clarificationRequired(int revision, RuntimeClarification clarification) {
        publish(ExecutionEventType.CLARIFICATION_REQUIRED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Clarification(revision, clarification, clarification.getQuestion()));
    }

    public void toolStarted(String invocationId, AgentToolDefinition definition) {
        publishTool(ExecutionEventType.TOOL_STARTED, invocationId, definition, null, null);
    }

    public void toolCompleted(String invocationId, AgentToolDefinition definition, long durationMillis) {
        publishTool(ExecutionEventType.TOOL_COMPLETED, invocationId, definition, durationMillis, null);
    }

    public void toolFailed(String invocationId, AgentToolDefinition definition, long durationMillis, String code) {
        publishTool(ExecutionEventType.TOOL_FAILED, invocationId, definition, durationMillis, code);
    }

    public void capabilityStarted(String invocationId, AgentInvocationState invocation) {
        publishCapability(ExecutionEventType.TOOL_STARTED, invocationId, invocation, null, null);
    }

    public void capabilityCompleted(String invocationId, AgentInvocationState invocation, long durationMillis) {
        publishCapability(ExecutionEventType.TOOL_COMPLETED, invocationId, invocation, durationMillis, null);
    }

    public void capabilityFailed(String invocationId, AgentInvocationState invocation, long durationMillis,
                                 String code) {
        publishCapability(ExecutionEventType.TOOL_FAILED, invocationId, invocation, durationMillis, code);
    }

    /** 人工门禁只暂停委派，不将尚未执行的操作记录为失败。 */
    public void capabilityWaitingHuman(String invocationId, AgentInvocationState invocation, long durationMillis) {
        publishCapability(ExecutionEventType.TOOL_WAITING_HUMAN, invocationId, invocation, durationMillis, null);
    }

    public void coordinationStarted(String invocationId, String operation, String displayName) {
        publish(ExecutionEventType.COORDINATION_STARTED, ExecutionStage.COORDINATION, invocationId,
                new ExecutionEventPayload.Coordination(operation, displayName, null, null));
    }

    public void coordinationCompleted(String invocationId, String operation, String displayName,
                                      long durationMillis) {
        publish(ExecutionEventType.COORDINATION_COMPLETED, ExecutionStage.COORDINATION, invocationId,
                new ExecutionEventPayload.Coordination(operation, displayName, durationMillis, null));
    }

    public void coordinationFailed(String invocationId, String operation, String displayName,
                                   long durationMillis, String code) {
        publish(ExecutionEventType.COORDINATION_FAILED, ExecutionStage.COORDINATION, invocationId,
                new ExecutionEventPayload.Coordination(operation, displayName, durationMillis, code));
    }

    public void humanRequired(RuntimeInterruption interruption) {
        publish(ExecutionEventType.HUMAN_REQUIRED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Human(interruption, "Agent等待人工确认"));
    }

    public void humanResolved(RuntimeInterruption interruption) {
        publish(ExecutionEventType.HUMAN_RESOLVED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Human(interruption, "人工确认已生效，Agent继续运行"));
    }

    public void reconciliationPending() {
        if (terminal.compareAndSet(false, true)) {
            publish(ExecutionEventType.RUN_RECONCILIATION_PENDING, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Failure("TOOL_RECONCILIATION_PENDING", "Tool执行结果等待人工对账"));
        }
    }

    public void complete() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        publish(ExecutionEventType.AGENT_COMPLETED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已完成"));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_COMPLETED, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Run("Agent运行已完成"));
        }
    }

    public void fail(Throwable exception) {
        if (exception instanceof AgentReconciliationPendingException) {
            reconciliationPending();
            return;
        }
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        String code = exception instanceof AgentRuntimeException runtime ? runtime.getCode() : "AGENT_RUNTIME_FAILED";
        publish(ExecutionEventType.AGENT_FAILED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Failure(code, "Agent调用失败"));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_FAILED, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Failure(code, "Agent运行失败"));
        }
    }

    public void cancel() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        publish(ExecutionEventType.AGENT_CANCELLED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已取消"));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_CANCELLED, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Run("Agent运行已取消"));
        }
    }

    public boolean isCancelled() {
        return sink.isCancelled();
    }

    public void acceptText(com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk chunk) {
        sink.onText(chunk);
    }

    public void mergeSequence(long delegatedLastSequenceNo) {
        sequence.accumulateAndGet(delegatedLastSequenceNo, Math::max);
    }

    public long lastSequenceNo() {
        return sequence.get();
    }

    private void publishStep(ExecutionEventType type, int revision, AgentInvocationState invocation,
                             AgentInvocationStatus status, String errorCode, String message) {
        publish(type, ExecutionStage.PLAN, new ExecutionEventPayload.Step(revision, invocation.getStep(), status,
                errorCode, message));
    }

    private void publishTool(ExecutionEventType type, String id, AgentToolDefinition definition, Long duration,
                             String code) {
        publish(type, ExecutionStage.TOOL, id, new ExecutionEventPayload.Tool(definition.getCode(),
                definition.getDisplayName(), duration, code));
    }

    private void publishCapability(ExecutionEventType type, String id, AgentInvocationState invocation,
                                   Long duration, String code) {
        RuntimeCapability capability = invocation.getCapability();
        ExecutionEventPayload.Tool.ToolBuilder payload = ExecutionEventPayload.Tool.builder()
                .toolCode(capability.getCode()).displayName(capability.getName())
                .durationMillis(duration).errorCode(code);
        if (capability.getType() == RuntimeCapabilityType.AGENT) {
            payload.delegatedAgentId(Objects.requireNonNull(capability.getTargetId(), "Agent能力目标不能为空"))
                    .taskRevision(invocation.getTaskRevision()).stepId(invocation.getStep().getId());
        }
        publish(type, ExecutionStage.TOOL, id, payload.build());
    }

    private void publish(ExecutionEventType type, ExecutionStage stage, ExecutionEventPayload payload) {
        publish(type, stage, null, payload);
    }

    // 同一 Agent 的异步任务必须按发布顺序完成持久化；跨 Agent 的全局序号仍由数据库分配。
    private synchronized void publish(ExecutionEventType type, ExecutionStage stage, String invocationId,
                         ExecutionEventPayload payload) {
        // 只有持久化成功才能推进游标，失败的事件不能占用后续事件的序号。
        long requestedSequenceNo = sequence.get() + 1;
        long persistedSequenceNo = sink.onEvent(ExecutionEvent.builder().runId(runId)
                .sequenceNo(requestedSequenceNo)
                .occurredAt(Instant.now()).type(type).stage(stage).agentId(agentId)
                .agentVersionId(agentVersionId).invocationId(invocationId).payload(payload).build());
        if (persistedSequenceNo < requestedSequenceNo) {
            throw new IllegalStateException("Runtime事件持久化序号不能倒退");
        }
        sequence.accumulateAndGet(persistedSequenceNo, Math::max);
    }

    private static void requireSkillActivation(AgentSkillBindingSnapshot binding, SkillActivationReason reason) {
        if (binding == null || binding.getSkill() == null || reason == null
                || !isReasonAllowed(binding.getSkill().getActivationMode(), reason)) {
            throw new IllegalArgumentException("Runtime只接受激活模式与原因一致的完整Skill事实");
        }
    }

    private static boolean isReasonAllowed(SkillActivationMode mode, SkillActivationReason reason) {
        return mode == SkillActivationMode.ALWAYS_ON && reason == SkillActivationReason.ALWAYS_ON
                || mode == SkillActivationMode.EXPLICIT_ONLY && reason == SkillActivationReason.EXPLICIT
                || mode == SkillActivationMode.MODEL_ROUTED && reason == SkillActivationReason.MODEL_ROUTED;
    }
}
