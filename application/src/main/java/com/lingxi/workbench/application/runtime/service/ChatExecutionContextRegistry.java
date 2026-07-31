package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.workflow.exception.WorkflowExecutionException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 使用 executionId 临时保存正在运行的 Agent 对话上下文。
 *
 * <p>该注册表只服务于 Workflow 到 Agent 的适配边界：Workflow 节点调用只携带
 * executionId，适配器据此找回不应暴露为图变量的可信对话上下文。Agent 核心服务
 * 直接接收 {@link ChatExecutionContext}，不依赖本注册表。</p>
 */
@Component
public class ChatExecutionContextRegistry {

    private final ConcurrentMap<String, ChatExecutionContext> contexts =
            new ConcurrentHashMap<>();

    /**
     * 注册 Workflow 执行上下文，executionId 必须全局唯一。
     *
     * @param context 本次 Agent 对话执行上下文
     * @throws WorkflowExecutionException executionId 重复时抛出
     */
    public void register(ChatExecutionContext context) {
        String executionId = context.getExecutionId();
        if (Objects.nonNull(contexts.putIfAbsent(executionId, context))) {
            throw new WorkflowExecutionException("Workflow executionId重复: " + executionId);
        }
    }

    /**
     * 获取已注册的 Workflow 执行上下文，不存在时抛出异常。
     *
     * @param executionId Workflow 图执行标识
     * @return 已注册的 Chat 持久化上下文
     * @throws WorkflowExecutionException 上下文不存在时抛出
     */
    public ChatExecutionContext require(String executionId) {
        ChatExecutionContext context = contexts.get(executionId);
        if (Objects.isNull(context)) {
            throw new WorkflowExecutionException("Workflow运行上下文不存在: " + executionId);
        }
        return context;
    }

    /**
     * 从注册表中移除指定执行上下文，Workflow 执行完成后必须调用。
     *
     * <p>无论 Workflow 正常完成、模型异常还是工具调用失败，都要清理上下文，
     * 避免用户会话信息长期滞留在进程内存中。</p>
     *
     * @param executionId 已结束的 Workflow 图执行标识
     */
    public void remove(String executionId) {
        contexts.remove(executionId);
    }
}
