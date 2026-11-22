package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.ConversationContextPrepareBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;

/**
 * 在路由、规划和 Agent 执行之前构建一次共享对话上下文。
 */
public interface ConversationContextService {

    ConversationContext prepare(ConversationContextPrepareBO input);
}
