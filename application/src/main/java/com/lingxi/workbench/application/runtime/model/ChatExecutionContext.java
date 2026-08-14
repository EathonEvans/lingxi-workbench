package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.tool.model.AgentExecutionBudget;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

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
 *     <li>存在 Workflow 上层调用时，以 executionId 临时注册供节点适配器读取；</li>
 *     <li>对话执行结束后释放，不跨请求复用。</li>
 * </ol>
 *
 * <p>它只在当前进程和当前对话任务内存活，不持久化，也不跨请求复用。</p>
 */
public final class ChatExecutionContext implements AgentExecutionBudget {

    /**
     * 本次 Agent 对话执行的唯一标识。
     *
     * <p>该标识由服务端生成，用于关联 Workflow 轨迹、模型调用和工具执行日志。</p>
     */
    @Getter
    private final String executionId;

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
     * 创建一次 Agent 对话专属的执行上下文。
     *
     * @param executionId      服务端生成的本次执行唯一标识
     * @param runtimeVariables 已完成基础变量初始化的运行时变量容器
     * @param turn             已落库且完成用户归属校验的当前对话轮次
     * @param eventPublisher   本次执行的文本和工具状态发布器
     */
    @Builder
    private ChatExecutionContext(String executionId, RuntimeVariables runtimeVariables, ChatTurnBO turn, ChatExecutionEventPublisher eventPublisher, MemoryConfig memoryConfig, ConversationContext conversationContext, ApplicationRuntimePolicy runtimePolicy, ContextBudgetPolicy contextBudget, Instant startedAt, TokenUsageAccumulator tokenUsageAccumulator, AtomicInteger modelCallCounter, AtomicInteger toolCallCounter, AtomicInteger delegationCounter) {
        this.executionId = executionId;
        this.runtimeVariables = Objects.requireNonNull(runtimeVariables, "运行时变量不能为空");
        this.turn = Objects.requireNonNull(turn, "对话轮次不能为空");
        this.eventPublisher = Objects.requireNonNullElseGet(eventPublisher, ChatExecutionEventPublisher::noop);
        this.memoryConfig = Objects.requireNonNullElseGet(memoryConfig, MemoryConfig::disabled);
        this.conversationContext = conversationContext;
        this.runtimePolicy = Objects.requireNonNullElseGet(runtimePolicy, ApplicationRuntimePolicy::defaults);
        this.contextBudget = Objects.requireNonNullElseGet(contextBudget, ContextBudgetPolicy::defaults);
        this.startedAt = Objects.requireNonNullElseGet(startedAt, Instant::now);
        this.tokenUsageAccumulator = Objects.requireNonNullElseGet(tokenUsageAccumulator, TokenUsageAccumulator::new);
        this.modelCallCounter = Objects.requireNonNullElseGet(modelCallCounter, AtomicInteger::new);
        this.toolCallCounter = Objects.requireNonNullElseGet(toolCallCounter, AtomicInteger::new);
        this.delegationCounter = Objects.requireNonNullElseGet(delegationCounter, AtomicInteger::new);
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
                .runtimeVariables(runtimeVariables.forkForQuery(query))
                .turn(turn)
                .eventPublisher(scopedPublisher)
                .memoryConfig(memoryConfig)
                .conversationContext(conversationContext)
                .runtimePolicy(runtimePolicy)
                .contextBudget(contextBudget)
                .startedAt(startedAt)
                .tokenUsageAccumulator(tokenUsageAccumulator)
                .modelCallCounter(modelCallCounter)
                .toolCallCounter(toolCallCounter)
                .delegationCounter(delegationCounter)
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
     * 在进入新的规划步骤或模型调用前校验Application总超时。
     */
    public void ensureWithinDeadline() {
        remainingTimeout(Duration.ofSeconds(runtimePolicy.getTimeoutSeconds()));
    }

    /**
     * 在所有执行分支共享的Application模型调用上限内占用一次额度。
     */
    @Override
    public void reserveModelCall() {
        reserveCalls(modelCallCounter, 1, runtimePolicy.getMaxModelCalls(), "Application单轮模型调用次数已达到上限");
    }

    /**
     * 占用预留的最终回答模型额度。
     *
     * <p>该调用只能用于禁用Tool、重规划和委派后的结果收口。</p>
     */
    @Override
    public void reserveFinalModelCall() {
        reserveCalls(modelCallCounter, 1, runtimePolicy.getMaxModelCalls(), "Application单轮模型调用次数已达到上限");
    }

    /**
     * 在所有执行分支共享的Application Tool调用上限内占用额度。
     */
    @Override
    public void reserveToolCalls(int calls) {
        if (calls < 1) {
            throw new IllegalArgumentException("Tool调用次数必须大于0");
        }
        reserveCalls(toolCallCounter, calls, runtimePolicy.getMaxToolCalls(), "Application单轮Tool调用次数已达到上限");
    }

    /** Supervisor每次调用领域专家前占用一次Application委派额度。 */
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

    /** 返回包含收口缓冲在内的最终回答可用Token额度。 */
    public int remainingFinalizationTokenBudget() {
        int remaining = hardTokenBudget() - usedTokenCount();
        if (remaining < 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application单轮执行超过Token硬上限");
        }
        return remaining;
    }

    /** 普通工作Token或预留模型调用只剩边界时，应停止继续探索并进入结果收口。 */
    public boolean shouldFinalize() {
        return hasReachedTokenSoftLimit() || remainingModelCalls() <= 1;
    }

    /** 是否已消耗完普通工作Token，后续宽限额度只能用于无Tool结果收口。 */
    public boolean hasReachedTokenSoftLimit() {
        return usedTokenCount() >= runtimePolicy.getTotalTokenBudget();
    }

    /** 是否仍可发起一次不带Tool的最终回答模型调用。 */
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
