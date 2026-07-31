package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

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
public final class ChatExecutionContext {

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

    /*
     * 一次 Agent 对话可能包含改写、工具判断和回答生成等多次模型调用，
     * Token 用量需要累计。
     * TokenUsageAccumulator 内部使用原子快照更新，因为模型结果可能由 Reactor
     * 回调线程记录，不能假定所有 recordUsage 调用始终发生在创建上下文的线程。
     */
    private final TokenUsageAccumulator tokenUsageAccumulator = new TokenUsageAccumulator();

    /**
     * 创建一次 Agent 对话专属的执行上下文。
     *
     * @param executionId      服务端生成的本次执行唯一标识
     * @param runtimeVariables 已完成基础变量初始化的运行时变量容器
     * @param turn             已落库且完成用户归属校验的当前对话轮次
     * @param eventPublisher   本次执行的文本和工具状态发布器
     */
    @Builder
    private ChatExecutionContext(String executionId, RuntimeVariables runtimeVariables, ChatTurnBO turn, ChatExecutionEventPublisher eventPublisher) {
        this.executionId = executionId;
        this.runtimeVariables = runtimeVariables;
        this.turn = turn;
        this.eventPublisher = Objects.requireNonNullElseGet(eventPublisher, ChatExecutionEventPublisher::noop);
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
     * <p>问题改写、工具调用后的继续生成等模型请求都必须记录到同一个上下文，
     * 最终才能将完整 Agent 执行成本写回 Assistant 消息。</p>
     *
     * @param usage 本次模型请求返回的 Token 用量
     */
    public void recordUsage(TokenUsage usage) {
        tokenUsageAccumulator.add(usage);
    }

    /**
     * 生成当前已累计用量的不可变快照。
     *
     * <p>Agent 执行完成后，该快照随最终回答一起交给对话服务写入 Assistant 消息。</p>
     */
    public TokenUsage tokenUsage() {
        return tokenUsageAccumulator.snapshot();
    }
}
