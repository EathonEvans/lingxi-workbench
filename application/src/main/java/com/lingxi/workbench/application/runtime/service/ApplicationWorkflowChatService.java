package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.WorkflowChatResult;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.context.LoginUser;

public interface ApplicationWorkflowChatService {

    /**
     * 返回入口 Agent 使用的模型实例，用于 Assistant 消息调用归属。
     */
    Long resolveEntryModelInstanceId(ApplicationRuntimeSnapshot snapshot);

    /**
     * 执行 ApplicationVersion 中冻结的 Workflow。
     */
    WorkflowChatResult execute(ApplicationRuntimeSnapshot snapshot, String query, LoginUser loginUser, ChatTurnBO turn);
}
