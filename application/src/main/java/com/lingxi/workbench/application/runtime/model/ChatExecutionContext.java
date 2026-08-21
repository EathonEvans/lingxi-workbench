package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.Builder;
import lombok.Getter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次 Agent 对话执行期间共享的服务端运行上下文。
 *
 * <p>它是 Agent 对话的主上下文，统一持有执行标识、运行时变量、持久化对话轮次
 * 与累计 Token 用量。Workflow 只是当前阶段的上层调用者，不参与这些状态的定义。</p>
 *
 * <p>生命周期固定为：</p>
 *
 * <ol>
 *     <li>对话任务开始执行时创建；</li>
 *     <li>Agent 前置处理、模型生成和工具调用共享同一个实例；</li>
 *     <li>Workflow 或 Supervisor 子调用通过显式参数派生隔离问题作用域；</li>
 *     <li>对话执行结束后释放，不跨请求复用。</li>
 * </ol>
 *
 * <p>它只在当前进程和当前对话任务内存活，不持久化，也不跨请求复用。</p>
 */
public final class ChatExecutionContext {

    private static final Duration ACTIVE_OUTPUT_WINDOW = Duration.ofSeconds(15);

    /**
     * 本次 Agent 对话执行的唯一标识。
     *
     * <p>该标识由服务端生成，用于关联 Workflow 轨迹、模型调用和工具执行日志。</p>
     */
    @Getter
    private final String executionId;

    /** 当前上下文代表整次运行入口，还是 Supervisor 内部隔离能力调用。 */
    @Getter
    private final ExecutionScope executionScope;

    /**
     * Prompt 渲染和各对话节点共享的运行时变量容器。
     *
     * <p>容器中的用户、当前问题、历史消息等值由对应处理阶段写入；后续节点只按
     * 变量定义读取，避免在多个 ChatContext 字段中重复维护相同状态。</p>
     */
    @Getter
    private final RuntimeVariables runtimeVariables;

    /**
     * 当前对话轮次的持久化标识。
     *
     * <p>其中的 conversationId、userMessageId 和 assistantMessageId 已在进入
     * Agent 执行前落库，可作为工具权限校验、任务取消和回答终态更新的可信依据。</p>
     */
    @Getter
    private final ChatTurnBO turn;
    /**
     * 将本次执行中的文本增量和工具状态发布给对话任务。
     */
    @Getter
    private final ChatExecutionEventPublisher eventPublisher;

    /**
     * 当前 ApplicationVersion 冻结的长期记忆配置。
     */
    @Getter
    private final MemoryConfig memoryConfig;

    /**
     * 路由前已经准备完成的共享对话上下文。
     */
    @Getter
    private final ConversationContext conversationContext;

    /**
     * 当前ApplicationVersion冻结的单轮执行总预算。
     */
    @Getter
    private final ApplicationRuntimePolicy runtimePolicy;

    /**
     * 当前ApplicationVersion冻结的上下文分区预算。
     */
    @Getter
    private final ContextBudgetPolicy contextBudget;

    /** 本轮由入口校验并在HITL恢复中保持不变的显式Skill code。 */
    @Getter
    private final List<String> explicitSkillCodes;

    /**
     * 本轮请求从上下文准备开始计算的统一执行起点。
     */
    @Getter
    private final Instant startedAt;

    /*
     * 一次 Agent 对话可能包含改写、工具判断和回答生成等多次模型调用，
     * Token 用量需要累计。
     * TokenUsageAccumulator 内部使用原子快照更新，因为模型结果可能由 Reactor
     * 回调线程记录，不能假定所有 recordUsage 调用始终发生在创建上下文的线程。
     */
    private final TokenUsageAccumulator tokenUsageAccumulator;
    /**
     * Planner、Workflow与全部Agent共享的模型调用计数。
     */
    private final AtomicInteger modelCallCounter;
    /**
     * 全部领域专家共享的工具调用计数。
     */
    private final AtomicInteger toolCallCounter;
    /**
     * Supervisor委派领域专家时共享的调用计数。
     */
    private final AtomicInteger delegationCounter;
    /**
     * 最后一个真实文本分片到达时间，供硬截止判断流式输出是否仍活跃。
     */
    private final AtomicReference<Instant> lastOutputAt;
    /**
     * 一次性流式宽限只能被同一轮执行领取一次。
     */
    private final AtomicBoolean timeoutGraceUsed;
    /**
     * MQ恢复时绑定的完整Runtime检查点；普通运行为空。
     */
    @Getter
    private final ExecutionResumeContext resumeContext;

    /**
     * 创建一次 Agent 对话专属的执行上下文。
     *
     * @param executionId      服务端生成的本次执行唯一标识
     * @param runtimeVariables 已完成基础变量初始化的运行时变量容器
     * @param turn             已落库且完成用户归属校验的当前对话轮次
     * @param eventPublisher   本次执行的文本和工具状态发布器
     */
    @Builder
    private ChatExecutionContext(String executionId, ExecutionScope executionScope, RuntimeVariables runtimeVariables, ChatTurnBO turn, ChatExecutionEventPublisher eventPublisher, MemoryConfig memoryConfig, ConversationContext conversationContext, ApplicationRuntimePolicy runtimePolicy, ContextBudgetPolicy contextBudget, List<String> explicitSkillCodes, Instant startedAt, TokenUsageAccumulator tokenUsageAccumulator, AtomicInteger modelCallCounter, AtomicInteger toolCallCounter, AtomicInteger delegationCounter, AtomicReference<Instant> lastOutputAt, AtomicBoolean timeoutGraceUsed, ExecutionResumeContext resumeContext) {
        this.executionId = executionId;
        this.executionScope = Objects.requireNonNullElse(executionScope, ExecutionScope.RUN);
        this.runtimeVariables = Objects.requireNonNull(runtimeVariables, "运行时变量不能为空");
        this.turn = Objects.requireNonNull(turn, "对话轮次不能为空");
        this.eventPublisher = Objects.requireNonNullElseGet(eventPublisher, ChatExecutionEventPublisher::noop);
        this.memoryConfig = Objects.requireNonNullElseGet(memoryConfig, MemoryConfig::disabled);
        this.conversationContext = conversationContext;
        this.runtimePolicy = Objects.requireNonNullElseGet(runtimePolicy, ApplicationRuntimePolicy::defaults);
        this.contextBudget = Objects.requireNonNullElseGet(contextBudget, ContextBudgetPolicy::defaults);
        this.explicitSkillCodes = List.copyOf(Objects.requireNonNullElseGet(explicitSkillCodes, List::of));
        this.startedAt = Objects.requireNonNullElseGet(startedAt, Instant::now);
        this.tokenUsageAccumulator = Objects.requireNonNullElseGet(tokenUsageAccumulator, TokenUsageAccumulator::new);
        this.modelCallCounter = Objects.requireNonNullElseGet(modelCallCounter, AtomicInteger::new);
        this.toolCallCounter = Objects.requireNonNullElseGet(toolCallCounter, AtomicInteger::new);
        this.delegationCounter = Objects.requireNonNullElseGet(delegationCounter, AtomicInteger::new);
        this.lastOutputAt = Objects.requireNonNullElseGet(lastOutputAt, AtomicReference::new);
        this.timeoutGraceUsed = Objects.requireNonNullElseGet(timeoutGraceUsed, AtomicBoolean::new);
        this.resumeContext = resumeContext;
    }

    /**
     * 为计划步骤创建独立的问题作用域。
     *
     * <p>步骤编译后的问题可能包含已完成依赖的结果，因此不能继续复用根任务的
     * {@code USER_QUERY}。其他可信上下文、预算、事件发布器和Token累计器保持共享。</p>
     */
    public ChatExecutionContext forkForQuery(String query) {
        return fork(query, eventPublisher);
    }

    /**
     * 为计划步骤创建独立问题和文本发布作用域。
     *
     * <p>步骤模型输出作为可观察过程事件发布，不写入最终Assistant正文；步骤完成后由
     * FinalSynthesis统一生成最终回答。</p>
     */
    public ChatExecutionContext forkForPlanStep(String query, String runId, Integer planNo, String stepId) {
        return fork(query, ChatExecutionEventPublisher.forPlanStep(eventPublisher, runId, planNo, stepId));
    }

    private ChatExecutionContext fork(String query, ChatExecutionEventPublisher scopedPublisher) {
        return ChatExecutionContext.builder()
                .executionId(executionId)
                .executionScope(ExecutionScope.INVOCATION)
                .runtimeVariables(runtimeVariables.forkForQuery(query))
                .turn(turn)
                .eventPublisher(scopedPublisher)
                .memoryConfig(memoryConfig)
                .conversationContext(conversationContext)
                .runtimePolicy(runtimePolicy)
                .contextBudget(contextBudget)
                .explicitSkillCodes(explicitSkillCodes)
                .startedAt(startedAt)
                .tokenUsageAccumulator(tokenUsageAccumulator)
                .modelCallCounter(modelCallCounter)
                .toolCallCounter(toolCallCounter)
                .delegationCounter(delegationCounter)
                .lastOutputAt(lastOutputAt)
                .timeoutGraceUsed(timeoutGraceUsed)
                .resumeContext(resumeContext)
                .build();
    }

    /**
     * 将单次操作超时收敛到Application本轮执行的剩余时间内。
     *
     * @param requested 当前能力或模型请求允许的最长时间
     * @return 不超过本轮剩余时间的实际超时
     */
    public Duration remainingTimeout(Duration requested) {
        if (Objects.isNull(requested) || requested.isZero() || requested.isNegative()) {
            throw new IllegalArgumentException("请求超时必须大于0");
        }
        Instant deadline = startedAt.plusSeconds(runtimePolicy.getTimeoutSeconds());
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application单轮执行超过总超时");
        }
        if (remaining.compareTo(requested) < 0) {
            return remaining;
        }
        return requested;
    }

    /**
     * 返回流式回答允许占用的最大剩余时间。
     *
     * <p>该值只扩大底层 HTTP 等待上限；是否真正使用宽限仍在硬截止到达时依据
     * 最近文本活动决定，不能让普通规划或工具调用静默突破硬截止。</p>
     */
    public Duration remainingStreamingTimeout(Duration requested) {
        if (Objects.isNull(requested) || requested.isZero() || requested.isNegative()) {
            throw new IllegalArgumentException("请求超时必须大于0");
        }
        Instant deadline = hardDeadline().plusSeconds(runtimePolicy.effectiveTimeoutGraceSeconds());
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new ExecutionDeadlineExceededException();
        }
        return remaining.compareTo(requested) < 0 ? remaining : requested;
    }

    /**
     * 记录真实文本输出活动；空 Usage 分片和工具状态不延长回答时限。
     */
    public void markOutputActivity() {
        lastOutputAt.set(Instant.now());
    }

    /**
     * 构造动态硬截止信号：硬截止时仅当最近十五秒仍有文本到达才领取一次宽限。
     */
    public Mono<Long> timeoutSignal() {
        Duration untilHardDeadline = Duration.between(Instant.now(), hardDeadline());
        if (untilHardDeadline.isZero() || untilHardDeadline.isNegative()) {
            return Mono.just(0L);
        }
        return Mono.delay(untilHardDeadline).flatMap(ignored -> {
            if (!tryUseActiveOutputGrace()) {
                return Mono.just(0L);
            }
            return Mono.delay(Duration.ofSeconds(runtimePolicy.effectiveTimeoutGraceSeconds()));
        });
    }

    /**
     * 在进入新的规划步骤或模型调用前校验Application总超时。
     */
    public void ensureWithinDeadline() {
        remainingTimeout(Duration.ofSeconds(runtimePolicy.getTimeoutSeconds()));
    }

    /**
     * 在所有执行分支共享的Application模型调用上限内占用一次额度。
     */
    public void reserveModelCall() {
        reserveCalls(modelCallCounter, 1, runtimePolicy.getMaxModelCalls(), "Application单轮模型调用次数已达到上限");
    }

    /**
     * 占用预留的最终回答模型额度。
     *
     * <p>该调用只能用于禁用Tool、重规划和委派后的结果收口。</p>
     */
    public void reserveFinalModelCall() {
        reserveCalls(modelCallCounter, 1, runtimePolicy.getMaxModelCalls(), "Application单轮模型调用次数已达到上限");
    }

    /**
     * 在所有执行分支共享的Application Tool调用上限内占用额度。
     */
    public void reserveToolCalls(int calls) {
        if (calls < 1) {
            throw new IllegalArgumentException("Tool调用次数必须大于0");
        }
        reserveCalls(toolCallCounter, calls, runtimePolicy.getMaxToolCalls(), "Application单轮Tool调用次数已达到上限");
    }

    /**
     * 时间或Token进入软边界后，ReAct循环必须停止继续探索并收口。
     */
    public boolean shouldFinalize() {
        return hasReachedSoftDeadline() || hasReachedTokenSoftLimit() || remainingModelCalls() <= 1;
    }

    /**
     * Supervisor每次调用领域专家前占用一次Application委派额度。
     */
    public void reserveDelegation() {
        reserveCalls(delegationCounter, 1, runtimePolicy.getMaxDelegations(), "Application单轮Agent委派次数已达到上限");
    }

    public int remainingModelCalls() {
        return Math.max(runtimePolicy.getMaxModelCalls() - modelCallCounter.get(), 0);
    }

    public int remainingToolCalls() {
        return Math.max(runtimePolicy.getMaxToolCalls() - toolCallCounter.get(), 0);
    }

    public int remainingDelegations() {
        return Math.max(runtimePolicy.getMaxDelegations() - delegationCounter.get(), 0);
    }

    public int usedModelCalls() {
        return modelCallCounter.get();
    }

    public int usedToolCalls() {
        return toolCallCounter.get();
    }

    public long activeElapsedMillis() {
        return Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
    }

    private static void reserveCalls(AtomicInteger counter, int calls, int limit, String message) {
        while (true) {
            int current = counter.get();
            if (calls > limit - current) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, message);
            }
            if (counter.compareAndSet(current, current + calls)) {
                return;
            }
        }
    }

    /**
     * 记录一次流式或最终模型响应携带的 Token 用量。
     *
     * <p>空 Token 字段表示供应商没有在该响应中返回用量，按零累计。</p>
     *
     * @param chunk 模型响应分片或最终响应
     */
    public void recordUsage(ChatStreamChunk chunk) {
        recordUsage(chunk.usage());
    }

    /**
     * 汇总一次非流式中间节点返回的 Token 用量。
     *
     * <p>问题重写、工具调用后的继续生成等模型请求都必须记录到同一个上下文，
     * 最终才能将完整 Agent 执行成本写回 Assistant 消息。模型完成后才会返回真实用量，
     * 因此这里仅做事实记账；即使单次响应越过硬线，也保留已经生成的完整结果，并由
     * {@link #hasFinalizationCapacity()} 阻止任何后续模型调用。</p>
     *
     * @param usage 本次模型请求返回的 Token 用量
     */
    public void recordUsage(TokenUsage usage) {
        tokenUsageAccumulator.add(usage);
    }

    /**
     * 返回扣除上下文准备和既有模型调用后的可用Token预算。
     */
    public int remainingTokenBudget() {
        int remaining = runtimePolicy.getTotalTokenBudget() - usedTokenCount();
        if (remaining < 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application Token工作额度已用尽，剩余额度仅用于最终回答");
        }
        return remaining;
    }

    /**
     * 返回包含收口缓冲在内的最终回答可用Token额度。
     */
    public int remainingFinalizationTokenBudget() {
        int remaining = hardTokenBudget() - usedTokenCount();
        if (remaining < 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application单轮执行超过Token硬上限");
        }
        return remaining;
    }

    /** 普通工作Token或预留模型调用只剩边界时，应停止继续探索并进入结果收口。 */
    /**
     * 是否已消耗完普通工作Token，后续宽限额度只能用于无Tool结果收口。
     */
    public boolean hasReachedTokenSoftLimit() {
        return usedTokenCount() >= runtimePolicy.getTotalTokenBudget();
    }

    /**
     * 是否仍可发起一次不带Tool的最终回答模型调用。
     */
    public boolean hasFinalizationCapacity() {
        return remainingModelCalls() > 0 && usedTokenCount() < hardTokenBudget();
    }

    private int usedTokenCount() {
        Integer totalTokens = tokenUsageAccumulator.snapshot().totalTokens();
        return Objects.requireNonNullElse(totalTokens, 0);
    }

    private int hardTokenBudget() {
        return runtimePolicy.effectiveTokenHardBudget();
    }

    /**
     * 总超时前预留固定收口窗口，进入窗口后不再启动工具、委派和新计划步骤。
     */
    public boolean hasReachedSoftDeadline() {
        return !Instant.now().isBefore(hardDeadline().minusSeconds(
                runtimePolicy.effectiveFinalizationReserveSeconds()));
    }

    public boolean isTimeoutGraceUsed() {
        return timeoutGraceUsed.get();
    }

    private Instant hardDeadline() {
        return startedAt.plusSeconds(runtimePolicy.getTimeoutSeconds());
    }

    private boolean tryUseActiveOutputGrace() {
        int graceSeconds = runtimePolicy.effectiveTimeoutGraceSeconds();
        if (graceSeconds < 1 || timeoutGraceUsed.get()) {
            return false;
        }
        Instant outputAt = lastOutputAt.get();
        if (Objects.isNull(outputAt) || outputAt.isBefore(Instant.now().minus(ACTIVE_OUTPUT_WINDOW))) {
            return false;
        }
        return timeoutGraceUsed.compareAndSet(false, true);
    }

    /**
     * 生成当前已累计用量的不可变快照。
     *
     * <p>Agent 执行完成后，该快照随最终回答一起交给对话服务写入 Assistant 消息。</p>
     */
    public TokenUsage tokenUsage() {
        return tokenUsageAccumulator.snapshot();
    }

    /**
     * 在计划步骤之间读取同一个Assistant任务的取消状态。
     */
    public boolean isCancelled() {
        return eventPublisher.isCancelled();
    }
}
