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
    /** 运行来源：CHAT（默认，用户消息）/ TRIGGER（定时触发器）。 */
    @Builder.Default
    private String runSource = "CHAT";
    /** 定时触发器主键；用户消息路径为空。 */
    private Long triggerId;
    /** 定时触发器的单次触发记录主键；用户消息路径为空。 */
    private Long fireId;
}
