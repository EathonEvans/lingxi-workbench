package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.application.runtime.model.CapabilityInvocationScope;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Workflow Agent 节点到 Agent 对话主服务的薄适配器。
 *
 * <p>这里只处理 Workflow 协议：解析节点输入并使用本次执行显式传入的可信对话上下文，
 * 再将 Application 侧有效约束投影为 AgentRunRequest，直接调用唯一 AgentRuntime。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatAgentNodeInvoker {

    private final AgentExecutionAdapter agentExecutionAdapter;

    public Map<String, Object> invoke(AgentNodeInvocation invocation, ApplicationRun run,
                                      CapabilityInvocationScope invocationScope) {
        Object queryValue = invocation.getInputs().get("query");
        if (!(queryValue instanceof String query) || query.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Agent节点输入query必须是非空字符串: " + invocation.getNodeId());
        }

        AgentRunResult result = agentExecutionAdapter.run(
                invocation.getAgentId(), invocation.getAgentVersionId(), query, run, invocationScope);
        if (result instanceof AgentRunResult.Interrupted) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "Workflow Agent节点不能使用Agent级HITL；请由Workflow交互节点管理暂停与恢复");
        }
        return Map.of("answer", result.content());
    }
}
