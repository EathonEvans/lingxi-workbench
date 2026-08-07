package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.PlannerInteractionResolution;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;

import java.util.Optional;

/** Planner澄清与风险确认的跨消息事务边界。 */
public interface InteractionSessionService {

    ChatClarificationEventBO open(InteractionSessionOpenCommand command);

    Optional<PlannerInteractionResolution> resolve(ApplicationRuntimeSnapshot application, ChatTurnBO turn, Long userId,
                                                   String query, ClarificationResponseRequest response);
}
