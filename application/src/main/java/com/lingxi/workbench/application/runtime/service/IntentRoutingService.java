package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.context.LoginUser;

/**
 * ApplicationVersion 绑定意图树后的运行时识别、澄清与兜底入口。
 */
public interface IntentRoutingService {


    IntentRoutingResult route(ApplicationRuntimeSnapshot application, String query, LoginUser loginUser,
                              ChatTurnBO turn, ClarificationResponseRequest clarificationResponse);
}
