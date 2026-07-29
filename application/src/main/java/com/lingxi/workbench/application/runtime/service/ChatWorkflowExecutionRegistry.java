package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ChatWorkflowExecutionContext;
import com.lingxi.workbench.workflow.exception.WorkflowExecutionException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 以 executionId 关联 Workflow 图状态和 Chat 持久化上下文。
 */
@Component
public class ChatWorkflowExecutionRegistry {

    private final ConcurrentMap<String, ChatWorkflowExecutionContext> contexts =
            new ConcurrentHashMap<>();

    /**
     * 注册 Workflow 执行上下文，executionId 必须全局唯一。
     *
     * @param executionId Workflow 图执行标识
     * @param context     关联的 Chat 持久化上下文
     * @throws WorkflowExecutionException executionId 重复时抛出
     */
    public void register(String executionId, ChatWorkflowExecutionContext context) {
        // 不存在则注册
        if (contexts.putIfAbsent(executionId, context) != null) {
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
    public ChatWorkflowExecutionContext require(String executionId) {
        ChatWorkflowExecutionContext context = contexts.get(executionId);
        if (context == null) {
            throw new WorkflowExecutionException("Workflow运行上下文不存在: " + executionId);
        }
        return context;
    }

    /**
     * 从注册表中移除指定执行上下文，Workflow 执行完成后必须调用。
     */
    public void remove(String executionId) {
        contexts.remove(executionId);
    }
}
