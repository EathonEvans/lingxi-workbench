package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;

public interface ApplicationChatExecutionService {

    /**
     * 识别用户意图，并执行叶子节点锁定的目标版本或直接返回路由结果。
     */
    ApplicationChatResult execute(ApplicationRuntimeSnapshot snapshot, String query, LoginUser loginUser, ChatTurnBO turn, ClarificationResponseRequest clarificationResponse, ChatExecutionEventPublisher eventPublisher);
}
