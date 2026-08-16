package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import lombok.Builder;
import lombok.Getter;

/** 创建一次 Application 顶级运行所需的可信元数据。 */
@Getter
@Builder
public class ExecutionRunStartCommand {

    private AppRuntimeSnapshot application;
    private ChatTurnBO turn;
    private Long userId;
    private String runId;
    private AgentRuntimeSnapshot orchestratorAgent;
    private String capabilityCode;
    private String executionMode;
}
