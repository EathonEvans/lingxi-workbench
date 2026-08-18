package com.lingxi.workbench.agentruntime.model;

import lombok.Getter;

/** Runtime 预算或迭代边界的稳定失败。 */
@Getter
public class AgentRuntimeLimitException extends RuntimeException {

    private final String code;

    public AgentRuntimeLimitException(String code, String message) {
        super(message);
        this.code = code;
    }
}
