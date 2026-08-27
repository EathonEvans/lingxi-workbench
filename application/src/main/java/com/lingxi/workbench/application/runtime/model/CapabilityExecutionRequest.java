package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * @param capability    ApplicationVersion锁定的能力
 * @param inputs        已按计划引用解析的命名输入
 * @param run           当前 Application 根运行
 * @param invocationScope 内部能力调用的隔离作用域；根调用为空
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CapabilityExecutionRequest {
    private final CapabilityDefinition capability;
    private final JSONObject inputs;
    private final ApplicationRun run;
    private final AgentRuntimeSnapshot agentConfig;
    private final IntentRoutingEvidence routingEvidence;
    private final Set<String> confirmedCapabilityCodes;
    private final CapabilityInvocationScope invocationScope;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }

    public String agentSessionId() {
        return invocationScope == null ? run.agentSessionId() : invocationScope.agentSessionId();
    }

    public ExecutionScope executionScope() {
        return invocationScope == null ? ExecutionScope.RUN : invocationScope.executionScope();
    }

    public ChatExecutionEventPublisher eventPublisher() {
        return invocationScope == null ? run.request().getEventPublisher() : invocationScope.eventPublisher();
    }
}
