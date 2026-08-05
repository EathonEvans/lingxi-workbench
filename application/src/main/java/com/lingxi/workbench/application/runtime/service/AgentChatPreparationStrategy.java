package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;

/**
 * 按 Agent 类型准备模型上下文并执行对应的前置处理链。
 *
 * <p>这是策略模式在 Agent 准备阶段的入口接口。每种 Agent 类型
 * （RAG_CHAT / SUPERVISOR）有各自的实现，由
 * {@code AgentChatPreparationStrategyRegistry} 在运行时按 agentCode 路由。</p>
 *
 * <h2>策略选择流程</h2>
 * <ol>
 *   <li>Application 运行端加载 AgentVersion 快照，获取 agentType</li>
 *   <li>通过 Registry 找到匹配的策略实现</li>
 *   <li>调用 {@link #prepare}：类型专属校验 + 上下文构建 + 处理链执行</li>
 *   <li>返回的 ModelChatContext 被传递给 ChatGateway 启动 LLM 调用</li>
 * </ol>
 *
 * <p>当前实现：</p>
 * <ul>
 *   <li>{@code RagChatPreparationStrategy} — 标准 RAG 问答 Agent，
 *       支持问题改写和知识检索</li>
 *   <li>{@code SupervisorPreparationStrategy} — 多 Agent 调度者，
 *       禁止问题改写和知识检索，由处理链注入委派工具和成员列表</li>
 * </ul>
 */
public interface AgentChatPreparationStrategy {

    /**
     * 返回本策略支持的稳定 Agent 类型编码。
     *
     * <p>返回值与 {@code ai_agent.agent_type} 字段和
     * {@code AgentChatConstants} 中的常量一致。</p>
     *
     * @return "RAG_CHAT" 或 "SUPERVISOR"
     */
    String agentType();

    /**
     * 执行类型专属校验、构建上下文并执行该类型的有序处理链。
     *
     * <p>典型实现步骤：</p>
     * <ol>
     *   <li>校验 AgentVersion 配置是否符合该类型的约束</li>
     *   <li>调用 {@code commonContext()} 加载 Prompt、记忆策略、运行时变量</li>
     *   <li>通过注入的 ChatProcessChain 执行类型专属后处理</li>
     * </ol>
     *
     * @param definition Agent 运行时定义快照
     * @param execution  对话执行上下文
     * @return 已装配并处理完成的模型上下文，可直接传给 ChatGateway
     */
    ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution);
}