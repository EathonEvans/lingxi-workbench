package com.lingxi.workbench.application.runtime.model;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * @param capability    ApplicationVersion锁定的能力
 * @param inputs        已按计划引用解析的命名输入
 * @param context       当前可信执行上下文
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CapabilityExecutionRequest {
    private final ApplicationCapabilityDefinition capability;
    private final JSONObject inputs;
    private final ChatExecutionContext context;
    private final AgentRuntimeSnapshot agent;
    private final IntentRoutingEvidence routingEvidence;
    private final Set<String> confirmedCapabilityCodes;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
