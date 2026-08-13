package com.lingxi.workbench.agentexecute.loop;

import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import lombok.Getter;

/**
 * Agent 循环达到运行边界时抛出的可分类异常。
 */
@Getter
public class AgentLoopLimitException extends AgentToolException {

    private final String errorCode;
    private final String clientMessage;

    public AgentLoopLimitException(String errorCode, String clientMessage, String internalMessage) {
        super(internalMessage);
        this.errorCode = errorCode;
        this.clientMessage = clientMessage;
    }
}
