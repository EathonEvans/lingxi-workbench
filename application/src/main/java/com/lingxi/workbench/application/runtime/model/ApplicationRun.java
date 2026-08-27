package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;

import java.util.Objects;

/**
 * AppRuntime 内一次根运行的四项权威事实。
 *
 * @param executionId 本次运行标识
 * @param request 已鉴权、已锁定版本的对话执行请求
 * @param conversation 已完成窗口、记忆和路由准备的对话数据
 * @param budget Application 单轮共享预算账本
 */
public record ApplicationRun(String executionId, ApplicationChatExecutionRequest request,
                             ConversationContext conversation, ApplicationExecutionBudget budget) {

    public ApplicationRun {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Application运行标识不能为空");
        }
        Objects.requireNonNull(request, "Application执行请求不能为空");
        Objects.requireNonNull(conversation, "Application对话准备结果不能为空");
        Objects.requireNonNull(budget, "Application预算账本不能为空");
    }

    public String agentSessionId() {
        return request.getResumeContext() == null ? executionId
                : request.getResumeContext().getCheckpoint().getAgentSessionId();
    }
}
