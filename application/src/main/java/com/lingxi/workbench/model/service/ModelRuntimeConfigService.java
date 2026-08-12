package com.lingxi.workbench.model.service;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;

/**
 * 为 application 业务提供经过完整校验的模型运行配置。
 */
public interface ModelRuntimeConfigService {

    /**
     * 获取当前默认且具备调用条件的对话模型配置。
     */
    ChatModelRuntimeConfig getDefaultChatModel();

    /**
     * 获取指定模型实例当前完整且可调用的对话模型配置。
     */
    ChatModelRuntimeConfig getChatModelConfig(Long instanceId);

    /**
     * 获取库中最新创建的可用 Embedding 模型配置。
     *
     * <p>后续将根据知识库绑定策略选择对应模型，当前阶段先取全局最新。</p>
     */
    EmbeddingModelRuntimeConfig getEmbeddingModelConfig(Long instanceId);
}
