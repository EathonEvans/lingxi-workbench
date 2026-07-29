package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 Workflow 运行所需的非业务图状态。
 *
 * <p>运行时变量、会话消息持久化信息不暴露给 Workflow 变量映射，Agent 节点
 * 通过 executionId 从注册表读取同一个运行快照。</p>
 */
public final class ChatWorkflowExecutionContext {

    private final RuntimeVariables runtimeVariables;
    private final ChatTurnBO turn;
    private final AtomicInteger inputTokens = new AtomicInteger();
    private final AtomicInteger outputTokens = new AtomicInteger();
    private final AtomicInteger totalTokens = new AtomicInteger();

    public ChatWorkflowExecutionContext(RuntimeVariables runtimeVariables, ChatTurnBO turn) {
        this.runtimeVariables = runtimeVariables;
        this.turn = turn;
    }

    public RuntimeVariables runtimeVariables() {
        return runtimeVariables;
    }

    public ChatTurnBO turn() {
        return turn;
    }

    public void recordUsage(ChatStreamChunk chunk) {
        recordUsage(chunk.inputTokens(), chunk.outputTokens(), chunk.totalTokens());
    }

    /**
     * 汇总非流式中间节点返回的 Token 用量。
     */
    public void recordUsage(Integer inputTokenCount, Integer outputTokenCount, Integer totalTokenCount) {
        inputTokens.addAndGet(value(inputTokenCount));
        outputTokens.addAndGet(value(outputTokenCount));
        totalTokens.addAndGet(value(totalTokenCount));
    }

    public MessageTokenUsageBO tokenUsage() {
        return new MessageTokenUsageBO(inputTokens.get(), outputTokens.get(), totalTokens.get());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
