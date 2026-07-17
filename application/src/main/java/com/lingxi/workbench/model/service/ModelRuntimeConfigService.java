package com.lingxi.workbench.model.service;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;

/** 为 application 业务提供经过完整校验的模型运行配置。 */
public interface ModelRuntimeConfigService {

    /**
     * 获取当前默认且具备调用条件的对话模型配置。
     *
     * <p>返回对象包含本次调用所需的解密 API Key，只允许在 application 到 infra-ai
     * 的内存调用链中使用，不得记录日志、写入缓存或通过接口返回。</p>
     */
    ChatModelRuntimeConfig getDefaultChatModel();
}
