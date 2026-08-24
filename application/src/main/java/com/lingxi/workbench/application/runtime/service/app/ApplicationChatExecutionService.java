package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;

public interface ApplicationChatExecutionService {

    /**
     * 构建共享上下文并执行意图路由或 fallback Agent。
     */
    ApplicationChatResult execute(ApplicationChatExecutionRequest request);
}
