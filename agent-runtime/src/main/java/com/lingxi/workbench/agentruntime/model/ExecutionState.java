package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.ExecutionStage;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus;
import com.lingxi.workbench.agentruntime.plan.RuntimeClarification;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import io.agentscope.core.state.Task;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 一次 Agent Runtime 调用唯一的可变执行事实。
 */
public final class ExecutionState {

    @Getter
    private final String runId;
    @Getter
    private final Long agentId;
    @Getter
    private final Long agentVersionId;
    private final ExecutionScope scope;
    @Getter
    private final ExecutionPolicy policy;
    private final RuntimeEventSink eventSink;
    private final AgentToolRuntimeConfig toolRuntimeConfig;
    private final AgentScopeCheckpoint checkpointSeed;
    private final AgentScopeCheckpointStore checkpointStore;
    private final Set<String> authorizedToolCodes;
    private final Map<String, AgentToolDefinition> toolDefinitions;
    private final Map<String, Integer> toolCallCounts = new HashMap<>();
    private final AtomicLong eventSequence;
    private final AtomicInteger modelCalls;
    private final AtomicInteger toolCalls;
    private final AtomicInteger toolCallsInCurrentModelRound = new AtomicInteger();
    private final TokenUsageAccumulator tokenUsage;
    private final boolean finalModelCallReserved;
    private final StringBuilder output = new StringBuilder();
    private boolean finalizationRequested;
    private String finalizationReason;
    private RuntimeInterruption interruption;
    @Getter
    private volatile ExecutionStatus status = ExecutionStatus.RUNNING;

    @Builder
    private ExecutionState(String runId, Long agentId, Long agentVersionId, ExecutionScope scope,
                           ExecutionPolicy policy,
                           RuntimeEventSink eventSink, AgentToolRuntimeConfig toolRuntimeConfig,
                           List<String> authorizedToolCodes, Long initialSequenceNo,
                           Integer initialModelCalls, Integer initialToolCalls, TokenUsage initialUsage,
                           AgentScopeCheckpoint checkpointSeed, AgentScopeCheckpointStore checkpointStore,
                           Boolean finalModelCallReserved) {
        if (Objects.isNull(runId) || runId.isBlank() || Objects.isNull(agentId)
                || Objects.isNull(agentVersionId)) {
            throw new IllegalArgumentException("Agent执行身份不能为空");
        }
        this.runId = runId;
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.scope = Objects.requireNonNullElse(scope, ExecutionScope.RUN);
        this.policy = Objects.requireNonNull(policy, "Agent执行策略不能为空");
        this.eventSink = Objects.requireNonNull(eventSink, "Runtime事件接收器不能为空");
        this.toolRuntimeConfig = toolRuntimeConfig;
        this.checkpointSeed = checkpointSeed;
        this.checkpointStore = checkpointStore;
        this.authorizedToolCodes = Set.copyOf(
                Objects.requireNonNullElseGet(authorizedToolCodes, List::of));
        this.toolDefinitions = resolveToolDefinitions(toolRuntimeConfig);
        if (!this.toolDefinitions.isEmpty()
                && (policy.getMaxModelCalls() < 2 || policy.getMaxToolCalls() < 1)) {
            throw new IllegalArgumentException("启用Tool时必须预留最终模型调用和Tool额度");
        }
        this.eventSequence = new AtomicLong(Objects.requireNonNullElse(initialSequenceNo, 0L));
        this.modelCalls = new AtomicInteger(Objects.requireNonNullElse(initialModelCalls, 0));
        this.toolCalls = new AtomicInteger(Objects.requireNonNullElse(initialToolCalls, 0));
        this.tokenUsage = new TokenUsageAccumulator();
        this.tokenUsage.initialize(initialUsage);
        this.finalModelCallReserved = Boolean.TRUE.equals(finalModelCallReserved);
    }

    /**
     * 从持久 Checkpoint 恢复运行事实；发布 SkillVersion 在 Application 入口重新校验。
     */
    public synchronized void restore(AgentScopeCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "AgentScope检查点不能为空");
        validateCheckpointIdentity(checkpoint);
        validateCheckpointPolicy(checkpoint);
        List<AgentScopeCheckpoint.ToolCallCount> counts = checkpoint.getToolCallCounts() == null
                ? List.of() : checkpoint.getToolCallCounts();
        toolCallCounts.clear();
        for (AgentScopeCheckpoint.ToolCallCount count : counts) {
            if (!toolDefinitions.containsKey(count.toolCode()) || count.calls() < 0) {
                throw new IllegalArgumentException("Checkpoint Tool调用计数不合法");
            }
            toolCallCounts.put(count.toolCode(), count.calls());
        }
        Integer restoredRoundCalls = Objects.requireNonNullElse(checkpoint.getToolCallsInCurrentModelRound(), 0);
        if (restoredRoundCalls < 0) {
            throw new IllegalArgumentException("Checkpoint当前模型轮次Tool调用计数不合法");
        }
        toolCallsInCurrentModelRound.set(restoredRoundCalls);
        finalizationRequested = Boolean.TRUE.equals(checkpoint.getFinalizationRequested());
        finalizationReason = checkpoint.getFinalizationReason();
        output.setLength(0);
        output.append(Objects.requireNonNullElse(checkpoint.getOutput(), ""));
    }

    /**
     * 根调用发布 Run 开始；每个隔离调用只发布自身 Agent 开始。
     */
    public void start() {
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_STARTED, ExecutionStage.RUNTIME,
                    new ExecutionEventPayload.Run("Agent运行已开始"));
        }
        publish(ExecutionEventType.AGENT_STARTED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已开始"));
    }

    /**
     * AgentScope 每轮进入任务协调前发布统一规划事件。
     */
    public void planning(int planNo) {
        ensureRunning();
        ExecutionEventType type = planNo == 1 ? ExecutionEventType.PLANNING : ExecutionEventType.REPLANNING;
        String message = planNo == 1 ? "正在规划任务" : "正在重新规划任务";
        publish(type, ExecutionStage.PLAN,
                new ExecutionEventPayload.Plan(planNo, List.of(), message));
    }

    /**
     * AgentScope 任务清单变更后追加可展示的任务快照。
     */
    public void tasksCreated(int revision, List<Task> tasks) {
        ensureRunning();
        List<Task> taskSnapshot = List.copyOf(Objects.requireNonNullElse(tasks, List.of()));
        if (taskSnapshot.isEmpty()) {
            throw new IllegalArgumentException("AgentScope任务快照不能为空");
        }
        publish(ExecutionEventType.PLAN_CREATED, ExecutionStage.PLAN,
                new ExecutionEventPayload.Plan(revision, taskSnapshot, "任务计划已更新"));
    }

    /**
     * 将 AgentScope 已发生的 Skill 使用投影为 Lingxi 审计事件，不保存第二套激活状态。
     */
    public synchronized void recordSkillActivation(AgentSkillBindingSnapshot binding, SkillActivationReason reason) {
        ensureRunning();
        requireSkillActivation(binding, reason);
        List<String> effectiveTools = List.copyOf(
                Objects.requireNonNullElse(binding.getEffectiveToolCodes(), List.of()));
        if (!authorizedToolCodes.containsAll(effectiveTools)) {
            throw new IllegalArgumentException("Skill有效Tool超出Agent授权范围：" + binding.getSkill().getCode());
        }
        publish(ExecutionEventType.SKILL_ACTIVATED, ExecutionStage.SKILL,
                ExecutionEventPayload.Skill.builder()
                        .skillId(binding.getSkillId())
                        .skillVersionId(binding.getSkillVersionId())
                        .versionNo(binding.getVersionNo())
                        .skillCode(binding.getSkill().getCode())
                        .checksum(binding.getChecksum())
                        .reason(reason)
                        .effectiveToolCodes(effectiveTools)
                        .message("Skill已激活：" + binding.getSkill().getName())
                        .build());
    }

    /**
     * 每次进入工作模型调用前原子占用额度并发布开始事件。
     */
    public int beforeModelCall() {
        ensureRunning();
        ensureNotCancelled();
        ensureTokenCapacity(policy.getMaxTotalTokens(), "AGENT_TOKEN_LIMIT");
        int callNo = modelCalls.incrementAndGet();
        int workingLimit = finalModelCallReserved
                ? policy.getMaxModelCalls() - 1 : policy.getMaxModelCalls();
        if (callNo > workingLimit) {
            modelCalls.decrementAndGet();
            throw new AgentRuntimeLimitException("AGENT_MODEL_CALL_LIMIT", "Agent工作模型调用次数已达到上限");
        }
        toolCallsInCurrentModelRound.set(0);
        publish(ExecutionEventType.MODEL_STARTED, ExecutionStage.MODEL,
                new ExecutionEventPayload.Model(callNo, TokenUsage.empty(), false, "模型调用已开始"));
        return callNo;
    }

    /**
     * 返回工作模型尚可使用的调用次数。
     */
    public int remainingModelCalls() {
        return Math.max(policy.getMaxModelCalls() - modelCalls.get(), 0);
    }

    /**
     * 禁用Tool的最终模型调用只使用预留调用和Token硬额度。
     */
    public int beforeFinalModelCall() {
        ensureRunning();
        ensureNotCancelled();
        ensureTokenCapacity(policy.getMaxFinalizationTokens(), "AGENT_TOKEN_HARD_LIMIT");
        requestFinalization(finalizationReason());
        int callNo = modelCalls.incrementAndGet();
        if (callNo > policy.getMaxModelCalls()) {
            modelCalls.decrementAndGet();
            throw new AgentRuntimeLimitException("AGENT_MODEL_CALL_LIMIT", "Agent最终模型调用次数已达到上限");
        }
        publish(ExecutionEventType.MODEL_STARTED, ExecutionStage.MODEL,
                new ExecutionEventPayload.Model(callNo, TokenUsage.empty(), true, "最终模型调用已开始"));
        return callNo;
    }

    /**
     * 记录模型返回的单次实际用量，并发布模型完成事件。
     */
    public synchronized void completeModelCall(int callNo, TokenUsage usage, boolean finalCall) {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        tokenUsage.add(usage);
        publish(ExecutionEventType.MODEL_COMPLETED, ExecutionStage.MODEL,
                new ExecutionEventPayload.Model(callNo, usage, finalCall, "模型调用已完成"));
    }

    /**
     * 合并隔离能力调用的实际消耗，并把事件序号推进到子调用之后。
     */
    public synchronized void recordDelegatedUsage(int delegatedModelCalls, int delegatedToolCalls,
                                                  TokenUsage usage, long delegatedLastSequenceNo) {
        ensureRunning();
        if (delegatedModelCalls < 0 || delegatedToolCalls < 0) {
            throw new IllegalArgumentException("委派调用计数不能小于0");
        }
        modelCalls.addAndGet(delegatedModelCalls);
        toolCalls.addAndGet(delegatedToolCalls);
        tokenUsage.add(usage);
        eventSequence.accumulateAndGet(delegatedLastSequenceNo, Math::max);
    }

    /**
     * Runtime 主循环在启动新步骤前检查取消状态。
     */
    public void ensureActive() {
        ensureRunning();
        ensureNotCancelled();
    }

    public boolean hasWorkingCapacity() {
        Integer usedTokens = tokenUsage.snapshot().totalTokens();
        return modelCalls.get() < policy.getMaxModelCalls() - 1
                && Objects.requireNonNullElse(usedTokens, 0) < policy.getMaxTotalTokens();
    }

    public boolean hasFinalizationCapacity() {
        Integer usedTokens = tokenUsage.snapshot().totalTokens();
        return modelCalls.get() < policy.getMaxModelCalls()
                && Objects.requireNonNullElse(usedTokens, 0) < policy.getMaxFinalizationTokens();
    }

    public void stepStarted(int taskRevision, AgentInvocationState invocation) {
        publishStep(ExecutionEventType.STEP_STARTED, taskRevision, invocation, AgentInvocationStatus.RUNNING,
                null, "步骤已开始");
    }

    public void stepReused(int taskRevision, AgentInvocationState invocation) {
        publishStep(ExecutionEventType.STEP_REUSED, taskRevision, invocation, AgentInvocationStatus.REUSED,
                null, "已复用相同副作用步骤结果");
    }

    public void stepCompleted(int taskRevision, AgentInvocationState invocation) {
        publishStep(ExecutionEventType.STEP_COMPLETED, taskRevision, invocation, AgentInvocationStatus.COMPLETED,
                null, "步骤已完成");
    }

    public void stepFailed(int taskRevision, AgentInvocationState invocation, String errorCode, String message) {
        publishStep(ExecutionEventType.STEP_FAILED, taskRevision, invocation, AgentInvocationStatus.FAILED,
                errorCode, message);
    }

    /**
     * AgentScope 请求用户补充信息时投影安全展示事件。
     */
    public void clarificationRequired(int taskRevision, RuntimeClarification clarification) {
        publish(ExecutionEventType.CLARIFICATION_REQUIRED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Clarification(taskRevision, clarification, clarification.getQuestion()));
    }

    /**
     * AgentScope Toolkit 在实际调用前占用 Lingxi 的全局与单 Tool 预算。
     */
    public synchronized void beforeToolCall(AgentToolDefinition definition) {
        ensureRunning();
        ensureNotCancelled();
        AgentToolDefinition authorized = requireToolDefinition(definition.getCode());
        if (!Objects.equals(authorized, definition)) {
            throw new IllegalArgumentException("AgentScope请求了未授权Tool定义：" + definition.getCode());
        }
        int usedByTool = toolCallCounts.getOrDefault(definition.getCode(), 0);
        if (toolCalls.get() >= policy.getMaxToolCalls()
                || usedByTool >= definition.effectiveMaxCallsPerExecution()
                || toolCallsInCurrentModelRound.get() >= definition.effectiveMaxCallsPerRound()) {
            requestFinalization("工具调用已达到平台收敛边界");
            throw new AgentRuntimeLimitException("AGENT_TOOL_CALL_LIMIT", "Agent工具调用次数已达到上限");
        }
        toolCalls.incrementAndGet();
        toolCallsInCurrentModelRound.incrementAndGet();
        toolCallCounts.merge(definition.getCode(), 1, Integer::sum);
    }

    /**
     * Token软上限或既有收口决定命中后不再执行模型刚请求的新Tool。
     */
    public boolean shouldFinalizeBeforeToolExecution() {
        return finalizationRequested || hasReached(tokenUsage.snapshot().totalTokens(), policy.getMaxTotalTokens());
    }

    /**
     * Tool执行后判断是否必须进入禁用Tool的最终回答。
     */
    public boolean shouldFinalize() {
        return finalizationRequested || modelCalls.get() >= policy.getMaxModelCalls() - 1
                || hasReached(tokenUsage.snapshot().totalTokens(), policy.getMaxTotalTokens());
    }

    public synchronized boolean isFinalizationRequested() {
        return finalizationRequested;
    }

    /**
     * 只记录首个收口原因，并发布一次 FINALIZING。
     */
    public synchronized void requestFinalization(String reason) {
        if (finalizationRequested) {
            return;
        }
        finalizationRequested = true;
        finalizationReason = Optional.ofNullable(reason).filter(value -> !value.isBlank())
                .orElse("运行预算已到边界或工具结果已经收敛");
        publish(ExecutionEventType.FINALIZING, ExecutionStage.AGENT,
                new ExecutionEventPayload.Run(finalizationReason));
    }

    public synchronized String finalizationReason() {
        return Optional.ofNullable(finalizationReason).filter(value -> !value.isBlank())
                .orElse("运行预算已到边界或工具结果已经收敛");
    }

    public void toolStarted(String invocationId, AgentToolDefinition definition) {
        publishTool(ExecutionEventType.TOOL_STARTED, invocationId, definition, null, null);
    }

    public void toolCompleted(String invocationId, AgentToolDefinition definition, long durationMillis) {
        publishTool(ExecutionEventType.TOOL_COMPLETED, invocationId, definition, durationMillis, null);
    }

    public void toolFailed(String invocationId, AgentToolDefinition definition, long durationMillis,
                           String errorCode) {
        publishTool(ExecutionEventType.TOOL_FAILED, invocationId, definition, durationMillis, errorCode);
    }

    /**
     * Supervisor 调用已发布 Capability 前占用全局 Tool 预算并投影统一审计事件。
     */
    public synchronized void capabilityDelegationStarted(String invocationId, String capabilityCode,
                                                         String displayName) {
        ensureRunning();
        ensureNotCancelled();
        if (capabilityCode == null || capabilityCode.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("发布能力Tool定义不完整");
        }
        if (toolCalls.get() >= policy.getMaxToolCalls()) {
            requestFinalization("发布能力调用已达到平台收敛边界");
            throw new AgentRuntimeLimitException("AGENT_TOOL_CALL_LIMIT", "Agent工具调用次数已达到上限");
        }
        toolCalls.incrementAndGet();
        toolCallsInCurrentModelRound.incrementAndGet();
        publishCapabilityTool(ExecutionEventType.TOOL_STARTED, invocationId, capabilityCode, displayName,
                null, null);
    }

    public void capabilityDelegationCompleted(String invocationId, String capabilityCode, String displayName,
                                              long durationMillis) {
        publishCapabilityTool(ExecutionEventType.TOOL_COMPLETED, invocationId, capabilityCode, displayName,
                durationMillis, null);
    }

    public void capabilityDelegationFailed(String invocationId, String capabilityCode, String displayName,
                                           long durationMillis, String errorCode) {
        publishCapabilityTool(ExecutionEventType.TOOL_FAILED, invocationId, capabilityCode, displayName,
                durationMillis, errorCode);
    }

    /**
     * 记录最终回答文本事实；Token 用量由模型 Advisor 统一记账。
     */
    public synchronized void appendOutput(String content) {
        if (Objects.nonNull(content) && !content.isEmpty()) {
            output.append(content);
        }
    }

    public synchronized void complete() {
        ensureRunning();
        status = ExecutionStatus.SUCCEEDED;
        publish(ExecutionEventType.AGENT_COMPLETED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已完成"));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_COMPLETED, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Run("Agent运行已完成"));
        }
    }

    public synchronized void completePartial(String reason) {
        ensureRunning();
        status = ExecutionStatus.PARTIAL;
        publish(ExecutionEventType.AGENT_PARTIAL, ExecutionStage.AGENT,
                new ExecutionEventPayload.Failure("BUDGET_LIMIT_PARTIAL", reason));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_PARTIAL, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Failure("BUDGET_LIMIT_PARTIAL", reason));
        }
    }

    public synchronized void fail(Throwable exception) {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        if (exception instanceof AgentReconciliationPendingException) {
            status = ExecutionStatus.RECONCILIATION_PENDING;
            publish(ExecutionEventType.RUN_RECONCILIATION_PENDING, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Failure("TOOL_RECONCILIATION_PENDING",
                            "外部能力结果等待本地对账"));
            return;
        }
        status = ExecutionStatus.FAILED;
        String code = exception instanceof AgentRuntimeLimitException limit
                ? limit.getCode()
                : "AGENT_RUNTIME_FAILED";
        publish(ExecutionEventType.AGENT_FAILED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Failure(code, "Agent调用失败"));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_FAILED, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Failure(code, "Agent运行失败"));
        }
    }

    public synchronized void cancel() {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        status = ExecutionStatus.CANCELLED;
        publish(ExecutionEventType.AGENT_CANCELLED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已取消"));
        if (scope.isRun()) {
            publish(ExecutionEventType.RUN_CANCELLED, ExecutionStage.TERMINAL,
                    new ExecutionEventPayload.Run("Agent运行已取消"));
        }
    }

    public boolean isCancelled() {
        return eventSink.isCancelled() || Thread.currentThread().isInterrupted();
    }

    public synchronized String output() {
        return output.toString();
    }

    public TokenUsage tokenUsage() {
        return tokenUsage.snapshot();
    }

    public int modelCalls() {
        return modelCalls.get();
    }

    public int toolCalls() {
        return toolCalls.get();
    }

    public boolean hasTools() {
        return !toolDefinitions.isEmpty();
    }

    public AgentToolRuntimeConfig toolRuntimeConfig() {
        if (Objects.isNull(toolRuntimeConfig)) {
            throw new IllegalStateException("当前Agent调用未启用Tool");
        }
        return toolRuntimeConfig;
    }

    public AgentScopeCheckpoint checkpointSeed() {
        return checkpointSeed;
    }

    public AgentScopeCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    /**
     * 领取批准调用前再次校验运行与取消状态。
     */
    public void ensureResumable() {
        ensureRunning();
        ensureNotCancelled();
    }

    public AgentToolDefinition requireToolDefinition(String toolCode) {
        AgentToolDefinition definition = toolDefinitions.get(toolCode);
        if (definition == null) {
            throw new IllegalArgumentException("Checkpoint请求了未授权Tool：" + toolCode);
        }
        return definition;
    }

    public synchronized List<AgentScopeCheckpoint.ToolCallCount> checkpointToolCallCounts() {
        return toolCallCounts.entrySet().stream()
                .map(entry -> new AgentScopeCheckpoint.ToolCallCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<String> checkpointAuthorizedToolCodes() {
        return authorizedToolCodes.stream().sorted().toList();
    }

    public int toolCallsInCurrentModelRound() {
        return toolCallsInCurrentModelRound.get();
    }

    public synchronized void interrupt(RuntimeInterruption value) {
        ensureRunning();
        interruption = Objects.requireNonNull(value, "人工中断事实不能为空");
        status = ExecutionStatus.INTERRUPTED;
        publish(ExecutionEventType.HUMAN_REQUIRED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Human(value, "Agent等待人工确认"));
    }

    public synchronized RuntimeInterruption interruption() {
        return interruption;
    }

    public synchronized void humanResolved(RuntimeInterruption value) {
        ensureRunning();
        publish(ExecutionEventType.HUMAN_RESOLVED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Human(value, "人工确认已生效，Agent继续运行"));
    }

    public synchronized void reconciliationPending(Long invocationId, RuntimeException exception) {
        requireCheckpointStore().markReconciliationPending(invocationId, exception);
        status = ExecutionStatus.RECONCILIATION_PENDING;
        publish(ExecutionEventType.RUN_RECONCILIATION_PENDING, ExecutionStage.TERMINAL,
                new ExecutionEventPayload.Failure("TOOL_RECONCILIATION_PENDING", "Tool执行结果等待人工对账"));
    }

    public long lastSequenceNo() {
        return eventSequence.get();
    }

    private AgentScopeCheckpointStore requireCheckpointStore() {
        return Objects.requireNonNull(checkpointStore, "Checkpoint存储器不能为空");
    }

    private void validateCheckpointIdentity(AgentScopeCheckpoint checkpoint) {
        if (!Objects.equals(runId, checkpoint.getRunId()) || !Objects.equals(agentId, checkpoint.getAgentId())
                || !Objects.equals(agentVersionId, checkpoint.getAgentVersionId())
                || !authorizedToolCodes.equals(Set.copyOf(checkpoint.getAuthorizedToolCodes() == null
                ? List.of() : checkpoint.getAuthorizedToolCodes()))) {
            throw new IllegalArgumentException("Checkpoint运行身份或Tool授权已经漂移");
        }
    }

    private void validateCheckpointPolicy(AgentScopeCheckpoint checkpoint) {
        if (!Objects.equals(checkpoint.getMaxModelCalls(), policy.getMaxModelCalls())
                || !Objects.equals(checkpoint.getMaxToolCalls(), policy.getMaxToolCalls())
                || !Objects.equals(checkpoint.getMaxTotalTokens(), policy.getMaxTotalTokens())
                || !Objects.equals(checkpoint.getMaxFinalizationTokens(), policy.getMaxFinalizationTokens())) {
            throw new IllegalArgumentException("Checkpoint执行策略已经漂移");
        }
    }

    private static Map<String, AgentToolDefinition> resolveToolDefinitions(AgentToolRuntimeConfig config) {
        if (Objects.isNull(config) || !config.enabled()) {
            return Map.of();
        }
        return config.getTools().stream().map(AgentTool::definition)
                .collect(Collectors.toUnmodifiableMap(AgentToolDefinition::getCode, Function.identity()));
    }

    private static void requireSkillActivation(AgentSkillBindingSnapshot binding, SkillActivationReason reason) {
        if (Objects.isNull(binding) || Objects.isNull(binding.getSkill()) || Objects.isNull(reason)
                || !isReasonAllowed(binding.getSkill().getActivationMode(), reason)) {
            throw new IllegalArgumentException("Runtime只接受激活模式与原因一致的完整Skill事实");
        }
    }

    private static boolean isReasonAllowed(SkillActivationMode mode, SkillActivationReason reason) {
        return mode == SkillActivationMode.ALWAYS_ON && reason == SkillActivationReason.ALWAYS_ON
                || mode == SkillActivationMode.EXPLICIT_ONLY && reason == SkillActivationReason.EXPLICIT
                || mode == SkillActivationMode.MODEL_ROUTED && reason == SkillActivationReason.MODEL_ROUTED;
    }

    private void publishTool(ExecutionEventType type, String invocationId, AgentToolDefinition definition,
                             Long durationMillis, String errorCode) {
        publishCapabilityTool(type, invocationId, definition.getCode(), definition.getDisplayName(), durationMillis,
                errorCode);
    }

    private void publishCapabilityTool(ExecutionEventType type, String invocationId, String toolCode,
                                       String displayName, Long durationMillis, String errorCode) {
        publish(type, ExecutionStage.TOOL, invocationId,
                new ExecutionEventPayload.Tool(toolCode, displayName, durationMillis, errorCode));
    }

    private void publishStep(ExecutionEventType type, int taskRevision, AgentInvocationState invocation,
                             AgentInvocationStatus status, String errorCode, String message) {
        publish(type, ExecutionStage.PLAN, new ExecutionEventPayload.Step(taskRevision, invocation.getStep(), status,
                errorCode, message));
    }

    private void ensureRunning() {
        if (status != ExecutionStatus.RUNNING) {
            throw new IllegalStateException("Agent运行已经进入终态");
        }
    }

    private void ensureNotCancelled() {
        if (isCancelled()) {
            throw new AgentRunCancelledException();
        }
    }

    private void ensureTokenCapacity(int limit, String errorCode) {
        Integer used = tokenUsage.snapshot().totalTokens();
        if (Objects.requireNonNullElse(used, 0) >= limit) {
            throw new AgentRuntimeLimitException(errorCode, "Agent Token额度已达到上限");
        }
    }

    private static boolean hasReached(Integer usedTokens, int limit) {
        return limit > 0 && Objects.nonNull(usedTokens) && usedTokens >= limit;
    }

    private void publish(ExecutionEventType type, ExecutionStage stage, ExecutionEventPayload payload) {
        publish(type, stage, null, payload);
    }

    private void publish(ExecutionEventType type, ExecutionStage stage, String invocationId,
                         ExecutionEventPayload payload) {
        long sequenceNo = eventSequence.incrementAndGet();
        eventSink.onEvent(ExecutionEvent.builder()
                .runId(runId)
                .sequenceNo(sequenceNo)
                .occurredAt(Instant.now())
                .type(type)
                .stage(stage)
                .agentId(agentId)
                .agentVersionId(agentVersionId)
                .invocationId(invocationId)
                .payload(payload)
                .build());
    }

}
