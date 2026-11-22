package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.repo.ModelCredentialRepo;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.model.repo.ModelProviderRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 将数据库中的模型配置解析为Embedding运行时配置。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeEmbeddingModelResolver {
    private final ModelInstanceRepo instanceRepo;
    private final ModelProviderRepo providerRepo;
    private final ModelCredentialRepo credentialRepo;
    private final SecretCipher secretCipher;

    /**
     * 校验知识库绑定的模型可用于向量化。
     */
    public void validate(Long instanceId) {
        requireAvailable(instanceId);
    }

    /**
     * 加载并解密一次调用所需的配置，明文密钥不会进入持久化对象。
     */
    public EmbeddingModelRuntimeConfig resolve(Long instanceId) {
        ModelBundle bundle = requireAvailable(instanceId);
        String apiKey;
        try {
            apiKey = secretCipher.decrypt(
                    bundle.credential().getSecretCiphertext(), bundle.credential().getSecretIv());
        } catch (IllegalStateException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Embedding模型密钥解密失败");
        }
        ModelInstanceEntity instance = bundle.instance();
        return EmbeddingModelRuntimeConfig.builder()
                .instanceId(instance.getId())
                .baseUrl(bundle.provider().getBaseUrl())
                .apiKey(apiKey)
                .modelCode(instance.getModelCode())
                .timeoutSeconds(instance.getTimeoutSeconds())
                .maxRetries(instance.getMaxRetries())
                .build();
    }

    private ModelBundle requireAvailable(Long instanceId) {
        ModelInstanceEntity instance = instanceRepo.findById(instanceId).orElseThrow(() ->
                new BusinessException(HttpStatusEnum.BAD_REQUEST, "Embedding模型不存在"));
        if (instance.getModelType() != ModelType.EMBEDDING || instance.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "请选择已启用的Embedding模型");
        }
        ModelProviderEntity provider = providerRepo.findById(instance.getProviderId()).orElseThrow(() ->
                new BusinessException(HttpStatusEnum.BAD_REQUEST, "Embedding模型供应商不存在"));
        ModelCredentialEntity credential = credentialRepo.findById(instance.getCredentialId()).orElseThrow(() ->
                new BusinessException(HttpStatusEnum.BAD_REQUEST, "Embedding模型密钥不存在"));
        if (provider.getStatus() != EnableStatus.ACTIVE
                || credential.getStatus() != ModelCredentialStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Embedding模型供应商或密钥不可用");
        }
        return new ModelBundle(instance, provider, credential);
    }

    /**
     * 解析出的 Embedding 模型运行三元组。
     *
     * @param instance   模型实例
     * @param provider   模型供应商
     * @param credential 模型凭证
     */
    private record ModelBundle(ModelInstanceEntity instance,
                               ModelProviderEntity provider,
                               ModelCredentialEntity credential) {
    }
}
