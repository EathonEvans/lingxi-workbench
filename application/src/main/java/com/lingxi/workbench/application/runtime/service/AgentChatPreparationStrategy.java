package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;

/**
 * 按 Agent 类型准备模型上下文。
 *
 * <p>这是策略模式在 Agent 准备阶段的入口接口。每种 Agent 类型
 * （REACT / SUPERVISOR）有各自的实现，由
 * {@code AgentChatPreparationStrategyRegistry} 在运行时按 Agent 类型路由。</p>
 *
 * <h2>策略选择流程</h2>
 * <ol>
 *   <li>Application 运行端加载 AgentVersion 快照，获取 agentType</li>
 *   <li>通过 Registry 找到匹配的策略实现</li>
 *   <li>调用 {@link #prepare}：类型专属校验和模型请求构建</li>
 *   <li>返回的 ModelChatContext 被传递给 ChatGateway 启动 LLM 调用</li>
 * </ol>
 *
 * <p>当前实现：</p>
 * <ul>
 *   <li>{@code ReactPreparationStrategy} — 通用推理与工具调用 Agent</li>
 *   <li>{@code SupervisorPreparationStrategy} — 多 Agent 调度者，
 *       由运行时协作快照注入委派工具和成员列表</li>
 * </ul>
 */
public interface AgentChatPreparationStrategy {

    /**
     * 返回本策略支持的 Agent 类型。
     *
     * @return Agent 类型
     */
    AgentType supportedAgentType();

    /**
     * 执行类型专属校验并使用共享上下文构建模型调用上下文。
     *
     * @param definition Agent 运行时定义快照
     * @param execution  对话执行上下文
     * @return 已装配并处理完成的模型上下文，可直接传给 ChatGateway
     */
    ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution);
}
