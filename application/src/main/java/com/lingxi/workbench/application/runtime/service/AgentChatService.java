package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;

/**
 * 执行一次完整 Agent 对话的唯一业务入口。
 *
 * <p>调用方只需要提供锁定的 Agent 版本、用户问题和服务端执行上下文，不需要了解
 * 共享上下文准备、问题上下文化、Prompt 渲染和工具调用等内部阶段。</p>
 */
@FunctionalInterface
public interface AgentChatService {

    /**
     * 使用指定 AgentVersion 完成一次回答。
     *
     * @param agentId        Agent 主键
     * @param agentVersionId 已发布 AgentVersion 主键
     * @param query          当前用户原始问题
     * @param context        本次对话共享的服务端执行上下文
     * @return Agent 最终生成的完整回答
     */
    String chat(Long agentId, Long agentVersionId, String query, ChatExecutionContext context);
}
