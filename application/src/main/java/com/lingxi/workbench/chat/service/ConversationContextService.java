package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.context.LoginUser;

/**
 * 在路由、规划和 Agent 执行之前构建一次共享对话上下文。
 */
public interface ConversationContextService {

    ConversationContext prepare(ApplicationRuntimeSnapshot application, String query, LoginUser loginUser, ChatTurnBO turn);
}
