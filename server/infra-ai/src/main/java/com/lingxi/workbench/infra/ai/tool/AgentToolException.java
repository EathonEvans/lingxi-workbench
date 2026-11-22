package com.lingxi.workbench.infra.ai.tool;

/**
 * 工具参数、执行或资源边界不满足时抛出的统一异常。
 */
public class AgentToolException extends RuntimeException {

    public AgentToolException(String message) {
        super(message);
    }

    public AgentToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
