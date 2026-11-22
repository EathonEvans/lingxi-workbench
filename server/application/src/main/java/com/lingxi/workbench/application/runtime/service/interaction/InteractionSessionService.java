package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.InteractionResolution;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;

import java.util.Optional;

/**
 * Agent 运行时澄清与风险确认的跨消息事务边界。
 */
public interface InteractionSessionService {

    ChatClarificationEventBO open(InteractionSessionOpenCommand command);

    Optional<InteractionResolution> resolve(AppRuntimeSnapshot application, ChatTurnBO turn, Long userId,
                                            String query, ClarificationResponseRequest response);
}
