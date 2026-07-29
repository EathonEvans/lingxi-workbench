package com.lingxi.workbench.admin.model.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;
import com.lingxi.workbench.admin.model.model.converter.ModelConverter;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.ModelInstancePageQuery;
import com.lingxi.workbench.admin.model.model.request.SaveModelInstanceRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelInstanceVO;
import com.lingxi.workbench.admin.model.repo.ModelCredentialRepo;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.model.repo.ModelProviderRepo;
import com.lingxi.workbench.admin.model.service.ModelConnectionTester;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** 模型实例业务实现。 */
@Service
@RequiredArgsConstructor
public class ModelInstanceServiceImpl implements ModelInstanceService {

    private final ModelProviderRepo providerRepo;
    private final ModelCredentialRepo credentialRepo;
    private final ModelInstanceRepo instanceRepo;
    private final AuditService auditService;
    private final SecretCipher cipher;
    private final ModelConnectionTester connectionTester;
    private final ModelConverter modelConverter;

    /**
     * 按关键字、供应商、模型类型和状态分页查询模型实例。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ModelInstanceVO> page(ModelInstancePageQuery query) {
        return instanceRepo.findPage(query).map(modelConverter::toInstanceVO);
    }

    /**
     * 新增或修改模型实例。
     *
     * <p>校验供应商归属、密钥可用性和编码唯一性。已创建实例不允许修改模型类型。</p>
     */
    @Override
    @Transactional
    public ModelInstanceVO save(
            Long id, SaveModelInstanceRequest request, Long operatorId) {
        ModelProviderEntity provider = requireProvider(request.getProviderId());
        ModelCredentialEntity credential = requireCredential(request.getCredentialId());
        // 校验密钥归属
        if (!credential.getProviderId().equals(provider.getId())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "密钥不属于所选供应商");
        }
        // 启用时需确保依赖的供应商和密钥均可用
        if (request.getStatus() == EnableStatus.ACTIVE) {
            requireAvailable(provider, credential);
        }

        String code = request.getCode().trim().toLowerCase();
        if (instanceRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "模型实例编码已存在");
        }

        ModelInstanceEntity entity = Objects.isNull(id) ? new ModelInstanceEntity() : requireInstance(id);
        // 已创建实例不允许修改模型类型
        if (Objects.nonNull(id) && entity.getModelType() != request.getModelType()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "已创建的模型实例不允许修改模型类型");
        }
        entity.setProviderId(provider.getId());
        entity.setCredentialId(credential.getId());
        entity.setName(request.getName().trim());
        entity.setCode(code);
        entity.setModelCode(request.getModelCode().trim());
        entity.setModelType(request.getModelType());
        entity.setCapabilities(normalizeCapabilities(request.getModelType(), request.getCapabilities()));
        // 非对话模型不设置对话相关参数
        boolean chatModel = request.getModelType() == ModelType.CHAT;
        entity.setTemperature(chatModel ? request.getTemperature() : null);
        entity.setTopP(chatModel ? request.getTopP() : null);
        entity.setMaxTokens(chatModel ? request.getMaxTokens() : null);
        entity.setContextWindow(request.getContextWindow());
        entity.setStopSequences(normalizeList(chatModel ? request.getStopSequences() : List.of()));
        entity.setExtensionParameters(ModelExtensionParameterValidator.normalize(
                chatModel ? request.getExtensionParameters() : null));
        entity.setTimeoutSeconds(request.getTimeoutSeconds());
        entity.setMaxRetries(request.getMaxRetries());
        entity.setStatus(request.getStatus());
        entity.setUpdatedBy(operatorId);
        // 新建实例时初始化默认值
        if (Objects.isNull(id)) {
            entity.setCreatedBy(operatorId);
            entity.setIsDefault(false);
            entity.setLastTestStatus(ModelConnectionTestStatus.UNTESTED);
        }

        instanceRepo.save(entity);
        auditService.record(
                "MODEL",
                "INSTANCE",
                entity.getId(),
                operatorId,
                Objects.isNull(id) ? "MODEL_CREATE" : "MODEL_UPDATE",
                "模型实例=" + code + "，类型=" + request.getModelType());
        return modelConverter.toInstanceVO(entity);
    }

    /**
     * 启用或停用模型实例。
     *
     * <p>默认实例不允许直接停用。启用时需确认依赖的供应商和密钥均可用。</p>
     */
    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        ModelInstanceEntity entity = requireInstance(id);
        // 默认实例不允许直接停用
        if (Boolean.TRUE.equals(entity.getIsDefault()) && status == EnableStatus.DISABLED) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "默认模型不能直接停用，请先设置新的默认模型");
        }
        // 启用时需确认依赖的供应商和密钥均可用
        if (status == EnableStatus.ACTIVE) {
            requireAvailable(
                    requireProvider(entity.getProviderId()),
                    requireCredential(entity.getCredentialId()));
        }

        entity.setStatus(status);
        entity.setUpdatedBy(operatorId);
        instanceRepo.save(entity);
        auditService.record(
                "MODEL", "INSTANCE", id, operatorId, "MODEL_STATUS_CHANGE", "状态=" + status);
    }

    /**
     * 将指定实例设置为当前模型类型的默认实例。
     *
     * <p>先清除同类型的旧默认标记，确保同一类型只有一个默认实例。</p>
     */
    @Override
    @Transactional
    public void setDefault(Long id, Long operatorId) {
        ModelInstanceEntity entity = requireInstance(id);
        if (entity.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "只能将启用模型设置为默认模型");
        }
        requireAvailable(
                requireProvider(entity.getProviderId()),
                requireCredential(entity.getCredentialId()));

        // 清除同类型的旧默认标记，确保同类型只有一个默认实例
        instanceRepo.clearDefault(entity.getModelType());
        entity.setIsDefault(true);
        entity.setUpdatedBy(operatorId);
        instanceRepo.save(entity);
        auditService.record(
                "MODEL", "INSTANCE", id, operatorId, "MODEL_SET_DEFAULT", "设置默认模型");
    }

    /**
     * 使用当前供应商、密钥和模型配置执行真实连通性测试，并回写测试结果。
     *
     * <p>解密 API Key 后发起真实接口调用，测试结果写回实例记录。</p>
     */
    @Override
    public ModelInstanceVO testConnection(Long id, Long operatorId) {
        ModelInstanceEntity entity = requireInstance(id);
        ModelProviderEntity provider = requireProvider(entity.getProviderId());
        ModelCredentialEntity credential = requireCredential(entity.getCredentialId());
        requireAvailable(provider, credential);

        ModelConnectionTestResult result;
        try {
            // 解密 API Key 后发起真实接口调用
            String apiKey = cipher.decrypt(
                    credential.getSecretCiphertext(), credential.getSecretIv());
            result = connectionTester.test(
                    provider.getProtocol(),
                    provider.getBaseUrl(),
                    apiKey,
                    entity.getModelCode(),
                    entity.getModelType(),
                    entity.getExtensionParameters(),
                    entity.getTimeoutSeconds());
        } catch (IllegalStateException exception) {
            result = ModelConnectionTestResult.failed(
                    0, "CREDENTIAL_ERROR", "模型密钥解密失败，请检查服务端主密钥配置");
        }

        // 将测试结果回写至实例记录
        OffsetDateTime now = OffsetDateTime.now();
        entity.setLastTestStatus(result.status());
        entity.setLastTestLatencyMs(result.latencyMs());
        entity.setLastTestErrorCode(result.errorCode());
        entity.setLastTestMessage(result.message());
        entity.setLastTestAt(now);
        entity.setUpdatedBy(operatorId);
        instanceRepo.save(entity);
        auditService.record(
                "MODEL",
                "INSTANCE",
                id,
                operatorId,
                "MODEL_CONNECTION_TEST",
                "测试状态=" + result.status() + ", 耗时=" + result.latencyMs() + "ms");
        return modelConverter.toInstanceVO(entity);
    }

    /** 校验供应商和密钥是否均处于可用状态，不满足则抛出业务异常。 */
    private static void requireAvailable(
            ModelProviderEntity provider, ModelCredentialEntity credential) {
        if (provider.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "所选供应商已停用");
        }
        if (credential.getStatus() != ModelCredentialStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "所选密钥不可用");
        }
    }

    /** 查询供应商，不存在时抛出 404 异常。 */
    private ModelProviderEntity requireProvider(Long id) {
        return providerRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型供应商不存在"));
    }

    /** 查询密钥，不存在时抛出 404 异常。 */
    private ModelCredentialEntity requireCredential(Long id) {
        return credentialRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型密钥不存在"));
    }

    /** 查询实例，不存在时抛出 404 异常。 */
    private ModelInstanceEntity requireInstance(Long id) {
        return instanceRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型实例不存在"));
    }

    /** 归一化模型能力列表：去重，并校验非对话模型不允许配置能力。 */
    private static List<ModelCapability> normalizeCapabilities(
            ModelType modelType, List<ModelCapability> capabilities) {
        List<ModelCapability> normalized = CollUtil.isEmpty(capabilities)
                ? List.of()
                : capabilities.stream().distinct().toList();
        if (modelType != ModelType.CHAT && CollUtil.isNotEmpty(normalized)) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "当前仅对话模型支持配置模型能力");
        }
        return normalized;
    }

    /** 归一化列表，null 或空时返回不可变空列表，否则返回不可变副本。 */
    private static <T> List<T> normalizeList(List<T> values) {
        return CollUtil.isEmpty(values) ? List.of() : List.copyOf(values);
    }

}
