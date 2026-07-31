package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Workflow Agent 节点到 Agent 对话主服务的薄适配器。
 *
 * <p>这里只处理 Workflow 协议：解析节点输入、通过 executionId 获取对话上下文，
 * 再调用 {@link AgentChatService}。RAG、Prompt、模型和工具逻辑全部属于 Agent
 * 对话服务，不能回流到本适配器。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatAgentNodeInvoker implements AgentNodeInvoker {

    private final AgentChatService agentChatService;
    private final ChatExecutionContextRegistry executionContextRegistry;

    @Override
    public Map<String, Object> invoke(AgentNodeInvocation invocation) {
        Object queryValue = invocation.getInputs().get("query");
        if (!(queryValue instanceof String query) || query.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Agent节点输入query必须是非空字符串: " + invocation.getNodeId());
        }

        ChatExecutionContext executionCtx = executionContextRegistry.require(invocation.getExecutionId());
        String answer = agentChatService.chat(invocation.getAgentId(), invocation.getAgentVersionId(), query, executionCtx);
        return Map.of("answer", answer);
    }
}
