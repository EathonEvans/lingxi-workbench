package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.agentruntime.model.AgentRuntimeException;

/** Application 能力适配器向 Runtime 返回的稳定失败分类。 */
public class RuntimeCapabilityException extends AgentRuntimeException {

    public RuntimeCapabilityException(String code, String clientMessage, boolean retryable, Throwable cause) {
        super(code, clientMessage, retryable, cause);
    }
}
