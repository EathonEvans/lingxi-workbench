package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.Objects;

/**
 * 一次 Agent 对话执行期间共享的服务端运行上下文。
 *
 * <p>它是 Agent 对话的主上下文，统一持有执行标识、运行时变量、持久化对话轮次
 * 与共享执行预算账本。Workflow 只是当前阶段的上层调用者，不参与这些状态的定义。</p>
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

    /** 当前执行各分支共享的模型、Tool、委派和 Token 预算账本。 */
    @Getter
    private final ApplicationExecutionBudget executionBudget;

    /**
     * 当前ApplicationVersion冻结的上下文分区预算。
     */
    @Getter
    private final ContextBudgetPolicy contextBudget;

    /** 本轮由入口校验并在HITL恢复中保持不变的显式Skill code。 */
    @Getter
    private final List<String> explicitSkillCodes;

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
    private ChatExecutionContext(String executionId, ExecutionScope executionScope, RuntimeVariables runtimeVariables,
                                 ChatTurnBO turn, ChatExecutionEventPublisher eventPublisher, MemoryConfig memoryConfig,
                                 ConversationContext conversationContext, ContextBudgetPolicy contextBudget,
                                 List<String> explicitSkillCodes, ApplicationExecutionBudget executionBudget,
                                 ExecutionResumeContext resumeContext) {
        this.executionId = executionId;
        this.executionScope = Objects.requireNonNullElse(executionScope, ExecutionScope.RUN);
        this.runtimeVariables = Objects.requireNonNull(runtimeVariables, "运行时变量不能为空");
        this.turn = Objects.requireNonNull(turn, "对话轮次不能为空");
        this.eventPublisher = Objects.requireNonNullElseGet(eventPublisher, ChatExecutionEventPublisher::noop);
        this.memoryConfig = Objects.requireNonNullElseGet(memoryConfig, MemoryConfig::disabled);
        this.conversationContext = conversationContext;
        this.contextBudget = Objects.requireNonNullElseGet(contextBudget, ContextBudgetPolicy::defaults);
        this.explicitSkillCodes = List.copyOf(Objects.requireNonNullElseGet(explicitSkillCodes, List::of));
        this.executionBudget = Objects.requireNonNull(executionBudget, "执行预算账本不能为空");
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
     * 为 AgentScope 任务委派创建独立问题和文本发布作用域。
     *
     * <p>步骤模型输出作为可观察过程事件发布，不写入最终Assistant正文；步骤完成后由
     * 父 Agent 统一生成最终回答。</p>
     */
    public ChatExecutionContext forkForTask(String query, String runId, Integer taskRevision, String taskId) {
        return fork(query, ChatExecutionEventPublisher.forPlanStep(eventPublisher, runId, taskRevision, taskId));
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
                .contextBudget(contextBudget)
                .explicitSkillCodes(explicitSkillCodes)
                .executionBudget(executionBudget)
                .resumeContext(resumeContext)
                .build();
    }

    /**
     * 在计划步骤之间读取同一个Assistant任务的取消状态。
     */
    public boolean isCancelled() {
        return eventPublisher.isCancelled();
    }
}
