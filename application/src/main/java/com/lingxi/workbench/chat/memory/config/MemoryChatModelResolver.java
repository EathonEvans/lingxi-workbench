package com.lingxi.workbench.chat.memory.config;

import com.lingxi.workbench.common.model.entity.ModelCredentialView;
import com.lingxi.workbench.common.model.entity.ModelInstanceView;
import com.lingxi.workbench.common.model.entity.ModelProviderView;
import com.lingxi.workbench.common.model.repo.mapper.ModelCredentialViewMapper;
import com.lingxi.workbench.common.model.repo.mapper.ModelInstanceViewMapper;
import com.lingxi.workbench.common.model.repo.mapper.ModelProviderViewMapper;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将 chatModelInstanceId 解析为 ChatCompletionGateway 所需的运行时配置。
 * 只读 ai_model_instance/provider/credential 表，不在代码中硬编码密钥。
 */
@Component
@RequiredArgsConstructor
public class MemoryChatModelResolver {
    private final ModelInstanceViewMapper instanceMapper;
    private final ModelProviderViewMapper providerMapper;
    private final ModelCredentialViewMapper credentialMapper;
    private final SecretCipher secretCipher;

    public ChatModelRuntimeConfig resolve(Long instanceId) {
        ModelInstanceView instance = java.util.Optional.ofNullable(
                instanceMapper.selectById(instanceId))
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "Chat模型不存在"));
        if (!"ACTIVE".equals(instance.getStatus())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "请选择已启用的Chat模型");
        }
        ModelProviderView provider = java.util.Optional.ofNullable(
                providerMapper.selectById(instance.getProviderId()))
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "模型供应商不存在"));
        ModelCredentialView credential = java.util.Optional.ofNullable(
                credentialMapper.selectById(instance.getCredentialId()))
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "模型密钥不存在"));
        String apiKey;
        try {
            apiKey = secretCipher.decrypt(
                    credential.getSecretCiphertext(), credential.getSecretIv());
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "模型密钥解密失败");
        }
        return ChatModelRuntimeConfig.builder()
                .instanceId(instance.getId())
                .baseUrl(provider.getBaseUrl())
                .apiKey(apiKey)
                .modelCode(instance.getModelCode())
                .timeoutSeconds(instance.getTimeoutSeconds())
                .maxRetries(instance.getMaxRetries())
                .maxTokens(2048)
                .build();
    }
}
