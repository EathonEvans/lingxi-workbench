package com.lingxi.workbench.agentruntime.model;

/** Runtime 预算或迭代边界的稳定失败。 */
public class AgentRuntimeLimitException extends AgentRuntimeException {

    public AgentRuntimeLimitException(String code, String message) {
        super(code, message, false, null);
    }
}
