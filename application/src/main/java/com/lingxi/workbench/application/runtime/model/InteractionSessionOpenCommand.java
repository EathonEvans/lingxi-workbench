package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import lombok.Builder;
import lombok.Getter;

/** 创建一次Planner澄清或风险确认会话所需的可信输入。 */
@Getter
@Builder
public class InteractionSessionOpenCommand {

    private AppRuntimeSnapshot application;
    private ChatTurnBO turn;
    private Long userId;
    private String runId;
    private InteractionSessionType interactionType;
    private InteractionSessionContext context;
}
