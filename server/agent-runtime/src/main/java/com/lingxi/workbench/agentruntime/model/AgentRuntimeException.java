package com.lingxi.workbench.agentruntime.model;

import lombok.Getter;

/** Agent Runtime 向 Application 暴露的稳定失败协议。 */
@Getter
public class AgentRuntimeException extends RuntimeException {

    private final String code;
    private final String clientMessage;
    private final boolean retryable;

    public AgentRuntimeException(String code, String clientMessage, boolean retryable, Throwable cause) {
        super(clientMessage, cause);
        this.code = code;
        this.clientMessage = clientMessage;
        this.retryable = retryable;
    }
}
