package com.lingxi.workbench.model.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模型运行配置服务实现。 */
@Service
@RequiredArgsConstructor
public class ModelRuntimeConfigServiceImpl implements ModelRuntimeConfigService {

    private static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private final ModelRuntimeConfigRepo configRepo;
    private final ModelRuntimeConfigConverter converter;
    private final SecretCipher secretCipher;

    /**
     * 获取当前默认且具备调用条件的对话模型配置。
     *
     * <p>校验模型、供应商和密钥状态均为 ACTIVE，解密密钥后组装运行时参数。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public ChatModelRuntimeConfig getDefaultChatModel() {
        ModelRuntimeConfigBO config = configRepo.findDefaultChatModel()
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "当前没有可用的默认对话模型"));
        if (!OPENAI_COMPATIBLE.equals(config.getProtocol())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "默认模型协议暂不支持");
        }
        String apiKey = secretCipher.decrypt(config.getSecretCiphertext(), config.getSecretIv());
        return converter.toRuntimeConfig(config, apiKey);
    }

    /**
     * 按实例 ID 获取当前可调用的完整对话模型配置。
     *
     * <p>校验模型、供应商和密钥状态均为 ACTIVE，解密密钥后组装运行时参数。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public ChatModelRuntimeConfig getChatModelConfig(Long instanceId) {
        ModelRuntimeConfigBO config = configRepo.findChatModel(instanceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "指定的对话模型不可用"));
        if (!OPENAI_COMPATIBLE.equals(config.getProtocol())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "指定的对话模型协议暂不支持");
        }
        String apiKey = secretCipher.decrypt(config.getSecretCiphertext(), config.getSecretIv());
        return converter.toRuntimeConfig(config, apiKey);
    }

    /**
     * 获取指定 ID 且当前可用的 Embedding 模型配置。
     *
     * <p>校验模型、供应商和密钥状态均为 ACTIVE，解密密钥后组装运行时参数。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public EmbeddingModelRuntimeConfig getEmbeddingModelConfig(Long instanceId) {
        ModelRuntimeConfigBO config = configRepo.getEmbedModelConfigBO(instanceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "当前没有可用的 Embedding 模型"));
        String apiKey = secretCipher.decrypt(config.getSecretCiphertext(), config.getSecretIv());
        return converter.toEmbeddingConfig(config, apiKey);
    }
}
