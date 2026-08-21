package com.lingxi.workbench.agentruntime.plan;

import lombok.Getter;

/** Application 能力适配器向 Runtime 返回的稳定失败分类。 */
@Getter
public class RuntimeCapabilityException extends RuntimeException {

    private final String code;
    private final String clientMessage;
    private final boolean retryable;

    public RuntimeCapabilityException(String code, String clientMessage, boolean retryable, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.clientMessage = clientMessage;
        this.retryable = retryable;
    }
}
