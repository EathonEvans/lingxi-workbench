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
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将记忆配置中引用的 embedding instanceId 解析为可传递给 EmbeddingGateway 的运行时配置。
 * 与知识库侧的 KnowledgeEmbeddingModelResolver 模式一致：只读 ai_model_instance 表，
 * 不在 yaml 或代码中硬编码 API Key / modelCode。
 */
@Component
@RequiredArgsConstructor
public class MemoryEmbeddingModelResolver {
    private final ModelInstanceViewMapper instanceMapper;
    private final ModelProviderViewMapper providerMapper;
    private final ModelCredentialViewMapper credentialMapper;
    private final SecretCipher secretCipher;

    /**
     * 校验 instanceId 对应的模型配置可用。
     */
    public void validate(Long instanceId) {
        requireAvailable(instanceId);
    }

    /**
     * 加载并解密一次调用所需的运行时配置。
     */
    public EmbeddingModelRuntimeConfig resolve(Long instanceId) {
        Bundle bundle = requireAvailable(instanceId);
        String apiKey;
        try {
            apiKey = secretCipher.decrypt(
                    bundle.credential().getSecretCiphertext(),
                    bundle.credential().getSecretIv());
        } catch (IllegalStateException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "Embedding模型密钥解密失败");
        }
        ModelInstanceView instance = bundle.instance();
        return EmbeddingModelRuntimeConfig.builder()
                .instanceId(instance.getId())
                .baseUrl(bundle.provider().getBaseUrl())
                .apiKey(apiKey)
                .modelCode(instance.getModelCode())
                .timeoutSeconds(instance.getTimeoutSeconds())
                .maxRetries(instance.getMaxRetries())
                .build();
    }

    private Bundle requireAvailable(Long instanceId) {
        ModelInstanceView instance = java.util.Optional.ofNullable(
                        instanceMapper.selectById(instanceId))
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "Embedding模型不存在"));
        if (!"EMBEDDING".equals(instance.getModelType())
                || !"ACTIVE".equals(instance.getStatus())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "请选择已启用的Embedding模型");
        }
        ModelProviderView provider = java.util.Optional.ofNullable(
                        providerMapper.selectById(instance.getProviderId()))
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "Embedding模型供应商不存在"));
        ModelCredentialView credential = java.util.Optional.ofNullable(
                        credentialMapper.selectById(instance.getCredentialId()))
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "Embedding模型密钥不存在"));
        if (!"ACTIVE".equals(provider.getStatus())
                || !"ACTIVE".equals(credential.getStatus())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "Embedding模型供应商或密钥不可用");
        }
        return new Bundle(instance, provider, credential);
    }

    private record Bundle(ModelInstanceView instance, ModelProviderView provider,
                          ModelCredentialView credential) {
    }
}
