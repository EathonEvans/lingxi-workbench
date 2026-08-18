package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.ExecutionStateSnapshot;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointContext;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.ExecutionStage;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.loop.AgentToolExecutionLedger;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 一次 Agent Runtime 调用唯一的可变执行事实。 */
public final class ExecutionState {

    private static final Duration ACTIVE_OUTPUT_WINDOW = Duration.ofSeconds(15);

    @Getter
    private final String runId;
    @Getter
    private final Long agentId;
    @Getter
    private final Long agentVersionId;
    @Getter
    private final ExecutionPolicy policy;
    @Getter
    private final Instant startedAt;
    private final RuntimeEventSink eventSink;
    private final AgentToolRuntimeConfig toolRuntimeConfig;
    private final RuntimeCheckpointContext checkpointContext;
    private final RuntimeCheckpointStore checkpointStore;
    private final Set<String> authorizedToolCodes;
    private final Map<String, AgentToolDefinition> toolDefinitions;
    private final Map<String, Integer> toolCallCounts = new HashMap<>();
    private final AgentToolExecutionLedger toolLedger = new AgentToolExecutionLedger();
    private final List<SkillActivation> skillActivations = new ArrayList<>();
    private final AtomicLong eventSequence;
    private final AtomicInteger modelCalls;
    private final AtomicInteger toolCalls;
    private final TokenUsageAccumulator tokenUsage;
    private final StringBuilder output = new StringBuilder();
    private final AtomicReference<Instant> lastOutputAt = new AtomicReference<>();
    private boolean finalizationRequested;
    private String finalizationReason;
    private RuntimeInterruption interruption;
    private ApprovedInvocation approvedInvocation;
    @Getter
    private volatile ExecutionStatus status = ExecutionStatus.RUNNING;

    @Builder
    private ExecutionState(String runId, Long agentId, Long agentVersionId, ExecutionPolicy policy,
                           RuntimeEventSink eventSink, AgentToolRuntimeConfig toolRuntimeConfig,
                           List<String> authorizedToolCodes, Long initialSequenceNo,
                           Integer initialModelCalls, Integer initialToolCalls, TokenUsage initialUsage,
                           Instant startedAt, RuntimeCheckpointContext checkpointContext,
                           RuntimeCheckpointStore checkpointStore) {
        if (Objects.isNull(runId) || runId.isBlank() || Objects.isNull(agentId)
                || Objects.isNull(agentVersionId)) {
            throw new IllegalArgumentException("Agent执行身份不能为空");
        }
        this.runId = runId;
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.policy = Objects.requireNonNull(policy, "Agent执行策略不能为空");
        this.eventSink = Objects.requireNonNull(eventSink, "Runtime事件接收器不能为空");
        this.toolRuntimeConfig = toolRuntimeConfig;
        this.checkpointContext = checkpointContext;
        this.checkpointStore = checkpointStore;
        this.authorizedToolCodes = Set.copyOf(
                Objects.requireNonNullElseGet(authorizedToolCodes, List::of));
        this.toolDefinitions = resolveToolDefinitions(toolRuntimeConfig);
        if (!this.toolDefinitions.isEmpty()
                && (policy.getMaxModelCalls() < 2 || policy.getMaxToolCalls() < 1)) {
            throw new IllegalArgumentException("启用Tool时必须预留最终模型调用和Tool额度");
        }
        this.startedAt = Objects.requireNonNullElseGet(startedAt, Instant::now);
        this.eventSequence = new AtomicLong(Objects.requireNonNullElse(initialSequenceNo, 0L));
        this.modelCalls = new AtomicInteger(Objects.requireNonNullElse(initialModelCalls, 0));
        this.toolCalls = new AtomicInteger(Objects.requireNonNullElse(initialToolCalls, 0));
        this.tokenUsage = new TokenUsageAccumulator();
        this.tokenUsage.initialize(initialUsage);
    }

    /** 从持久Checkpoint恢复运行事实，并校验当前加载的冻结版本没有漂移。 */
    public synchronized void restore(ExecutionStateSnapshot snapshot, List<SkillActivation> currentSkills) {
        Objects.requireNonNull(snapshot, "ExecutionState检查点不能为空");
        validateSnapshotIdentity(snapshot);
        validateSnapshotPolicy(snapshot);
        validateSnapshotSkills(snapshot, currentSkills);
        List<ExecutionStateSnapshot.ToolCallCount> counts = snapshot.getToolCallCounts() == null
                ? List.of() : snapshot.getToolCallCounts();
        toolCallCounts.clear();
        for (ExecutionStateSnapshot.ToolCallCount count : counts) {
            if (!toolDefinitions.containsKey(count.toolCode()) || count.calls() < 0) {
                throw new IllegalArgumentException("Checkpoint Tool调用计数不合法");
            }
            toolCallCounts.put(count.toolCode(), count.calls());
        }
        toolLedger.restore(snapshot.getToolLedger());
        skillActivations.clear();
        skillActivations.addAll(currentSkills == null ? List.of() : currentSkills);
        finalizationRequested = Boolean.TRUE.equals(snapshot.getFinalizationRequested());
        finalizationReason = snapshot.getFinalizationReason();
        output.setLength(0);
        output.append(Objects.requireNonNullElse(snapshot.getOutput(), ""));
    }

    /** 发布 Run 与 Agent 的唯一开始事件。 */
    public void start() {
        publish(ExecutionEventType.RUN_STARTED, ExecutionStage.RUNTIME,
                new ExecutionEventPayload.Run("Agent运行已开始"));
        publish(ExecutionEventType.AGENT_STARTED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已开始"));
    }

    /** Planner 每轮进入模型决策前发布统一规划事件。 */
    public void planning(int planNo) {
        ensureRunning();
        ExecutionEventType type = planNo == 1 ? ExecutionEventType.PLANNING : ExecutionEventType.REPLANNING;
        String message = planNo == 1 ? "正在规划任务" : "正在重新规划任务";
        publish(type, ExecutionStage.PLAN, new ExecutionEventPayload.Plan(planNo, null, message));
    }

    /** Planner typed 决策通过业务校验后发布统一决策事件。 */
    public void planCreated(int planNo, PlannerDecision decision) {
        ensureRunning();
        publish(ExecutionEventType.PLAN_CREATED, ExecutionStage.PLAN,
                new ExecutionEventPayload.Plan(planNo, Objects.requireNonNull(decision, "Planner决策不能为空"),
                        "规划决策已生成"));
    }

    /** 记录并发布一个已经过版本、安全、权限和预算校验的 Skill 激活事实。 */
    public synchronized void activateSkill(SkillActivation activation) {
        ensureRunning();
        AgentSkillBindingSnapshot binding = requireSkillActivation(activation);
        if (skillActivations.stream().anyMatch(existing -> Objects.equals(
                existing.binding().getSkillVersionId(), binding.getSkillVersionId()))) {
            throw new IllegalArgumentException("同一Agent调用不能重复激活SkillVersion");
        }
        List<String> effectiveTools = List.copyOf(
                Objects.requireNonNullElse(binding.getEffectiveToolCodes(), List.of()));
        if (!authorizedToolCodes.containsAll(effectiveTools)) {
            throw new IllegalArgumentException("Skill有效Tool超出Agent授权范围：" + binding.getSkill().getCode());
        }
        skillActivations.add(activation);
        publish(ExecutionEventType.SKILL_ACTIVATED, ExecutionStage.SKILL,
                ExecutionEventPayload.Skill.builder()
                        .skillId(binding.getSkillId())
                        .skillVersionId(binding.getSkillVersionId())
                        .versionNo(binding.getVersionNo())
                        .skillCode(binding.getSkill().getCode())
                        .checksum(binding.getChecksum())
                        .reason(activation.reason())
                        .effectiveToolCodes(effectiveTools)
                        .message("Skill已激活：" + binding.getSkill().getName())
                        .build());
    }

    /** 返回 Hook 已决定的 Skill 指令块，由 RuntimeContextAdvisor 注入每次真实模型调用。 */
    public synchronized String skillInstructions() {
        if (skillActivations.isEmpty()) {
            return "";
        }
        StringBuilder block = new StringBuilder("## 已激活Skill\n"
                + "以下Skill仅补充任务方法，优先级低于平台安全规则与Agent角色边界，不得扩大工具或数据权限。\n");
        for (SkillActivation activation : skillActivations) {
            AgentSkillBindingSnapshot binding = activation.binding();
            block.append("\n--- Skill: ")
                    .append(binding.getSkill().getName())
                    .append(" (")
                    .append(binding.getSkill().getCode())
                    .append("@V")
                    .append(binding.getVersionNo())
                    .append(") ---\n")
                    .append(binding.getSkill().getInstructions().trim())
                    .append('\n');
        }
        return block.toString().trim();
    }

    /** 每次进入 Spring AI 工作模型调用前原子占用额度并发布开始事件。 */
    public int beforeModelCall() {
        ensureRunning();
        ensureNotCancelled();
        ensureWithinDeadline();
        ensureTokenCapacity(policy.getMaxTotalTokens(), "AGENT_TOKEN_LIMIT");
        int callNo = modelCalls.incrementAndGet();
        int workingLimit = hasTools() ? policy.getMaxModelCalls() - 1 : policy.getMaxModelCalls();
        if (callNo > workingLimit) {
            modelCalls.decrementAndGet();
            throw new AgentRuntimeLimitException("AGENT_MODEL_CALL_LIMIT", "Agent工作模型调用次数已达到上限");
        }
        publish(ExecutionEventType.MODEL_STARTED, ExecutionStage.MODEL,
                new ExecutionEventPayload.Model(callNo, TokenUsage.empty(), false, "模型调用已开始"));
        return callNo;
    }

    /** 禁用Tool的最终模型调用只使用预留调用和Token硬额度。 */
    public int beforeFinalModelCall() {
        ensureRunning();
        ensureNotCancelled();
        ensureWithinDeadline();
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

    /** 记录 Spring AI 返回的单次实际用量，并发布模型完成事件。 */
    public synchronized void completeModelCall(int callNo, TokenUsage usage, boolean finalCall) {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        tokenUsage.add(usage);
        publish(ExecutionEventType.MODEL_COMPLETED, ExecutionStage.MODEL,
                new ExecutionEventPayload.Model(callNo, usage, finalCall, "模型调用已完成"));
    }

    /** 按全局、单轮和单Tool上限筛选模型请求的调用，未知Tool永不执行。 */
    public synchronized ToolCallSelection selectToolCalls(List<AssistantMessage.ToolCall> requestedCalls) {
        ensureRunning();
        ensureNotCancelled();
        ensureWithinDeadline();
        List<AssistantMessage.ToolCall> calls = List.copyOf(
                Objects.requireNonNull(requestedCalls, "模型工具调用列表不能为空"));
        List<AssistantMessage.ToolCall> candidates = calls.stream()
                .filter(call -> {
                    AgentToolDefinition definition = toolDefinitions.get(call.name());
                    return definition != null && definition.getSecurityPolicy().isApprovalRequired();
                })
                .findFirst()
                .map(List::of)
                .orElse(calls);
        List<AssistantMessage.ToolCall> approved = new ArrayList<>();
        Map<String, Integer> roundCounts = new HashMap<>();
        String limitReason = null;
        for (AssistantMessage.ToolCall call : candidates) {
            AgentToolDefinition definition = toolDefinitions.get(call.name());
            if (Objects.isNull(definition)) {
                limitReason = "模型请求了未授权工具";
                continue;
            }
            int roundCount = roundCounts.getOrDefault(call.name(), 0);
            int executionCount = toolCallCounts.getOrDefault(call.name(), 0);
            boolean totalLimited = toolCalls.get() + approved.size() >= policy.getMaxToolCalls();
            boolean roundLimited = roundCount >= definition.effectiveMaxCallsPerRound();
            boolean executionLimited = executionCount >= definition.effectiveMaxCallsPerExecution();
            if (totalLimited || roundLimited || executionLimited) {
                limitReason = "工具调用已达到平台收敛边界";
                continue;
            }
            approved.add(call);
            roundCounts.merge(call.name(), 1, Integer::sum);
            toolCallCounts.merge(call.name(), 1, Integer::sum);
        }
        toolCalls.addAndGet(approved.size());
        boolean totalLimited = policy.getMaxToolCalls() > 0 && toolCalls.get() >= policy.getMaxToolCalls();
        boolean limited = approved.size() < candidates.size() || totalLimited
                || hasReachedToolExecutionLimit(approved);
        if (limited) {
            requestFinalization(Objects.requireNonNullElse(limitReason, "工具已达到单次执行上限"));
        }
        return new ToolCallSelection(List.copyOf(approved), limited, finalizationReason());
    }

    /** Token软上限或既有收口决定命中后不再执行模型刚请求的新Tool。 */
    public boolean shouldFinalizeBeforeToolExecution() {
        return finalizationRequested || hasReached(tokenUsage.snapshot().totalTokens(), policy.getMaxTotalTokens());
    }

    /** Tool执行后判断是否必须进入禁用Tool的最终回答。 */
    public boolean shouldFinalize() {
        return finalizationRequested || modelCalls.get() >= policy.getMaxModelCalls() - 1
                || hasReached(tokenUsage.snapshot().totalTokens(), policy.getMaxTotalTokens());
    }

    public synchronized boolean isFinalizationRequested() {
        return finalizationRequested;
    }

    /** 只记录首个收口原因，并发布一次 FINALIZING。 */
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

    public Optional<String> findReusableToolResult(AgentToolDefinition definition, String input) {
        return toolLedger.findReusableResult(definition, input);
    }

    public void recordSuccessfulToolResult(AgentToolDefinition definition, String input, String result) {
        toolLedger.recordSuccessfulResult(definition, input, result);
    }

    public String toolReuseResponse() {
        return toolLedger.reuseResponse();
    }

    public void toolStarted(String invocationId, AgentToolDefinition definition) {
        publishTool(ExecutionEventType.TOOL_STARTED, invocationId, definition, null, null);
    }

    public void toolReused(String invocationId, AgentToolDefinition definition) {
        publishTool(ExecutionEventType.TOOL_REUSED, invocationId, definition, 0L, null);
    }

    public void toolCompleted(String invocationId, AgentToolDefinition definition, long durationMillis) {
        publishTool(ExecutionEventType.TOOL_COMPLETED, invocationId, definition, durationMillis, null);
    }

    public void toolFailed(String invocationId, AgentToolDefinition definition, long durationMillis,
                           String errorCode) {
        publishTool(ExecutionEventType.TOOL_FAILED, invocationId, definition, durationMillis, errorCode);
    }

    /** 记录最终回答文本事实；Token 用量由模型 Advisor 统一记账。 */
    public synchronized void appendOutput(String content) {
        if (Objects.nonNull(content) && !content.isEmpty()) {
            output.append(content);
            lastOutputAt.set(Instant.now());
        }
    }

    public synchronized void complete() {
        ensureRunning();
        status = ExecutionStatus.SUCCEEDED;
        publish(ExecutionEventType.AGENT_COMPLETED, ExecutionStage.AGENT,
                new ExecutionEventPayload.Agent(agentId, agentVersionId, "Agent调用已完成"));
        publish(ExecutionEventType.RUN_COMPLETED, ExecutionStage.TERMINAL,
                new ExecutionEventPayload.Run("Agent运行已完成"));
    }

    public synchronized void completePartial(String reason) {
        ensureRunning();
        status = ExecutionStatus.PARTIAL;
        publish(ExecutionEventType.RUN_PARTIAL, ExecutionStage.TERMINAL,
                new ExecutionEventPayload.Failure("TIME_LIMIT_PARTIAL", reason));
    }

    public synchronized void fail(Throwable exception) {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        status = ExecutionStatus.FAILED;
        String code = exception instanceof AgentRuntimeLimitException limit
                ? limit.getCode()
                : exception instanceof AgentExecutionDeadlineExceededException
                        ? "AGENT_EXECUTION_TIMEOUT"
                        : "AGENT_RUNTIME_FAILED";
        publish(ExecutionEventType.RUN_FAILED, ExecutionStage.TERMINAL,
                new ExecutionEventPayload.Failure(code, "Agent运行失败"));
    }

    public synchronized void cancel() {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        status = ExecutionStatus.CANCELLED;
        publish(ExecutionEventType.RUN_CANCELLED, ExecutionStage.TERMINAL,
                new ExecutionEventPayload.Run("Agent运行已取消"));
    }

    public boolean isCancelled() {
        return eventSink.isCancelled() || Thread.currentThread().isInterrupted();
    }

    public boolean isDeadlineReached() {
        return !Instant.now().isBefore(startedAt.plus(policy.getTimeout()));
    }

    public boolean canUseActiveOutputGrace() {
        Instant outputAt = lastOutputAt.get();
        return !policy.getActiveOutputGrace().isZero() && Objects.nonNull(outputAt)
                && !outputAt.isBefore(Instant.now().minus(ACTIVE_OUTPUT_WINDOW));
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

    public RuntimeCheckpointContext checkpointContext() {
        return checkpointContext;
    }

    public RuntimeCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    /** 领取批准调用前再次校验取消与硬截止，过期运行绝不执行外部副作用。 */
    public void ensureResumable() {
        ensureRunning();
        ensureNotCancelled();
        ensureWithinDeadline();
    }

    public AgentToolDefinition requireToolDefinition(String toolCode) {
        AgentToolDefinition definition = toolDefinitions.get(toolCode);
        if (definition == null) {
            throw new IllegalArgumentException("Checkpoint请求了未授权Tool：" + toolCode);
        }
        return definition;
    }

    /** 生成不包含框架对象和敏感Tool参数的执行状态快照。 */
    public synchronized ExecutionStateSnapshot snapshot() {
        List<ExecutionStateSnapshot.ToolCallCount> counts = toolCallCounts.entrySet().stream()
                .map(entry -> new ExecutionStateSnapshot.ToolCallCount(entry.getKey(), entry.getValue()))
                .toList();
        List<ExecutionStateSnapshot.SkillState> skills = skillActivations.stream()
                .map(activation -> new ExecutionStateSnapshot.SkillState(
                        activation.binding().getSkillVersionId(), activation.binding().getChecksum()))
                .toList();
        return ExecutionStateSnapshot.builder()
                .runId(runId)
                .agentId(agentId)
                .agentVersionId(agentVersionId)
                .maxModelCalls(policy.getMaxModelCalls())
                .maxToolCalls(policy.getMaxToolCalls())
                .maxTotalTokens(policy.getMaxTotalTokens())
                .maxFinalizationTokens(policy.getMaxFinalizationTokens())
                .timeoutMillis(policy.getTimeout().toMillis())
                .activeOutputGraceMillis(policy.getActiveOutputGrace().toMillis())
                .modelCalls(modelCalls.get())
                .toolCalls(toolCalls.get())
                .tokenUsage(tokenUsage.snapshot())
                .lastSequenceNo(eventSequence.get())
                .activeElapsedMillis(Duration.between(startedAt, Instant.now()).toMillis())
                .finalizationRequested(finalizationRequested)
                .finalizationReason(finalizationReason)
                .output(output.toString())
                .toolCallCounts(counts)
                .toolLedger(toolLedger.snapshot())
                .skills(skills)
                .authorizedToolCodes(authorizedToolCodes.stream().sorted().toList())
                .build();
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

    /** 恢复时登记已被数据库CAS领取的原始Tool调用。 */
    public synchronized void prepareApprovedInvocation(Long invocationId,
                                                       RuntimeCheckpoint.ToolCallSnapshot call) {
        ensureRunning();
        approvedInvocation = new ApprovedInvocation(
                Objects.requireNonNull(invocationId, "审批调用主键不能为空"),
                Objects.requireNonNull(call, "审批ToolCall不能为空"));
    }

    public synchronized void humanResolved(RuntimeInterruption value) {
        ensureRunning();
        publish(ExecutionEventType.HUMAN_RESOLVED, ExecutionStage.HUMAN,
                new ExecutionEventPayload.Human(value, "人工确认已生效，Agent继续运行"));
    }

    /** RuntimeToolCallback只消费一次已领取调用，普通Tool返回空。 */
    public synchronized Optional<Long> consumeApprovedInvocation(AgentToolDefinition definition, String input) {
        if (approvedInvocation == null) {
            return Optional.empty();
        }
        RuntimeCheckpoint.ToolCallSnapshot call = approvedInvocation.call();
        if (!Objects.equals(call.getName(), definition.getCode()) || !Objects.equals(call.getArguments(), input)) {
            throw new IllegalStateException("恢复的ToolCall与已批准参数不一致");
        }
        Long invocationId = approvedInvocation.invocationId();
        approvedInvocation = null;
        return Optional.of(invocationId);
    }

    public void completeApprovedInvocation(Long invocationId, String result) {
        requireCheckpointStore().complete(invocationId, result);
    }

    public void failApprovedInvocation(Long invocationId, RuntimeException exception) {
        requireCheckpointStore().fail(invocationId, exception);
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

    private RuntimeCheckpointStore requireCheckpointStore() {
        return Objects.requireNonNull(checkpointStore, "Checkpoint存储器不能为空");
    }

    private void validateSnapshotIdentity(ExecutionStateSnapshot snapshot) {
        if (!Objects.equals(runId, snapshot.getRunId()) || !Objects.equals(agentId, snapshot.getAgentId())
                || !Objects.equals(agentVersionId, snapshot.getAgentVersionId())
                || !authorizedToolCodes.equals(Set.copyOf(snapshot.getAuthorizedToolCodes() == null
                        ? List.of() : snapshot.getAuthorizedToolCodes()))) {
            throw new IllegalArgumentException("Checkpoint运行身份或Tool授权已经漂移");
        }
    }

    private void validateSnapshotPolicy(ExecutionStateSnapshot snapshot) {
        if (!Objects.equals(snapshot.getMaxModelCalls(), policy.getMaxModelCalls())
                || !Objects.equals(snapshot.getMaxToolCalls(), policy.getMaxToolCalls())
                || !Objects.equals(snapshot.getMaxTotalTokens(), policy.getMaxTotalTokens())
                || !Objects.equals(snapshot.getMaxFinalizationTokens(), policy.getMaxFinalizationTokens())
                || !Objects.equals(snapshot.getTimeoutMillis(), policy.getTimeout().toMillis())
                || !Objects.equals(snapshot.getActiveOutputGraceMillis(), policy.getActiveOutputGrace().toMillis())) {
            throw new IllegalArgumentException("Checkpoint执行策略已经漂移");
        }
    }

    private static void validateSnapshotSkills(ExecutionStateSnapshot snapshot,
                                               List<SkillActivation> currentSkills) {
        List<ExecutionStateSnapshot.SkillState> persisted = snapshot.getSkills() == null
                ? List.of() : snapshot.getSkills();
        List<SkillActivation> current = currentSkills == null ? List.of() : currentSkills;
        Map<Long, String> currentChecksums = current.stream().collect(Collectors.toUnmodifiableMap(
                activation -> activation.binding().getSkillVersionId(),
                activation -> activation.binding().getChecksum()));
        boolean matched = persisted.size() == currentChecksums.size() && persisted.stream().allMatch(skill ->
                Objects.equals(currentChecksums.get(skill.skillVersionId()), skill.checksum()));
        if (!matched) {
            throw new IllegalArgumentException("Checkpoint SkillVersion已经漂移");
        }
    }

    private static Map<String, AgentToolDefinition> resolveToolDefinitions(AgentToolRuntimeConfig config) {
        if (Objects.isNull(config) || !config.enabled()) {
            return Map.of();
        }
        return config.getTools().stream().map(AgentTool::definition)
                .collect(Collectors.toUnmodifiableMap(AgentToolDefinition::getCode, Function.identity()));
    }

    private AgentSkillBindingSnapshot requireSkillActivation(SkillActivation activation) {
        if (Objects.isNull(activation) || Objects.isNull(activation.binding())
                || Objects.isNull(activation.binding().getSkill()) || Objects.isNull(activation.reason())
                || activation.binding().getSkill().getActivationMode() != SkillActivationMode.ALWAYS_ON) {
            throw new IllegalArgumentException("Runtime只接受完整的ALWAYS_ON Skill激活事实");
        }
        return activation.binding();
    }

    private boolean hasReachedToolExecutionLimit(List<AssistantMessage.ToolCall> approved) {
        return approved.stream().map(AssistantMessage.ToolCall::name).distinct().anyMatch(toolCode -> {
            AgentToolDefinition definition = toolDefinitions.get(toolCode);
            int limit = definition.effectiveMaxCallsPerExecution();
            return limit < Integer.MAX_VALUE && toolCallCounts.getOrDefault(toolCode, 0) >= limit;
        });
    }

    private void publishTool(ExecutionEventType type, String invocationId, AgentToolDefinition definition,
                             Long durationMillis, String errorCode) {
        publish(type, ExecutionStage.TOOL, invocationId,
                new ExecutionEventPayload.Tool(definition.getCode(), definition.getDisplayName(),
                        durationMillis, errorCode));
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

    private void ensureWithinDeadline() {
        if (isDeadlineReached()) {
            throw new AgentExecutionDeadlineExceededException();
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

    /**
     * @param approvedCalls 本轮获准执行的Tool调用
     * @param limited       是否因平台边界截断或命中上限
     * @param reason        进入最终回答时的安全原因
     */
    public record ToolCallSelection(List<AssistantMessage.ToolCall> approvedCalls, boolean limited,
                                    String reason) {
    }

    private record ApprovedInvocation(Long invocationId, RuntimeCheckpoint.ToolCallSnapshot call) {
    }
}
