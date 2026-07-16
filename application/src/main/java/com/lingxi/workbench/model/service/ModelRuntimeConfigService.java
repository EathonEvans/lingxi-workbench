package com.lingxi.workbench.model.service;

import com.lingxi.workbench.infra.ai.chat.ChatModelRuntimeConfig;

/** 为 application 业务提供经过完整校验的模型运行配置。 */
public interface ModelRuntimeConfigService {

    /** 获取当前默认对话模型配置。 */
    ChatModelRuntimeConfig getDefaultChatModel();
}
