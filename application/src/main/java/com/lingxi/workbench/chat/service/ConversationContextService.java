package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.context.LoginUser;

/**
 * 在路由、规划和 Agent 执行之前构建一次共享对话上下文。
 */
public interface ConversationContextService {

    ConversationContext prepare(AppRuntimeSnapshot application, String query, LoginUser loginUser, ChatTurnBO turn);
}
