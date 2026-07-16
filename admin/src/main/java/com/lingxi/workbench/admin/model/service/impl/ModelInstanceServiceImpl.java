package com.lingxi.workbench.admin.model.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.converter.ModelConverter;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.SaveModelInstanceRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelInstanceVO;
import com.lingxi.workbench.admin.model.repo.ModelCredentialRepo;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.model.repo.ModelProviderRepo;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** 模型实例业务实现。 */
@Service
@RequiredArgsConstructor
public class ModelInstanceServiceImpl implements ModelInstanceService {

    private static final String UNTESTED = "UNTESTED";

    private final ModelProviderRepo providerRepo;
    private final ModelCredentialRepo credentialRepo;
    private final ModelInstanceRepo instanceRepo;
    private final AuditService auditService;
    private final ModelConverter modelConverter;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ModelInstanceVO> page(PageQuery query) {
        return instanceRepo.findPage(query).map(modelConverter::toInstanceVO);
    }

    @Override
    @Transactional
    public ModelInstanceVO save(
            Long id, SaveModelInstanceRequest request, Long operatorId) {
        ModelProviderEntity provider = requireProvider(request.providerId());
        ModelCredentialEntity credential = requireCredential(request.credentialId());
        if (!credential.getProviderId().equals(provider.getId())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "密钥不属于所选供应商");
        }
        if (request.status() == EnableStatus.ACTIVE) {
            requireAvailable(provider, credential);
        }

        String code = request.code().trim().toLowerCase();
        if (instanceRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "模型实例编码已存在");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ModelInstanceEntity entity = id == null ? new ModelInstanceEntity() : requireInstance(id);
        entity.setProviderId(provider.getId());
        entity.setCredentialId(credential.getId());
        entity.setName(request.name().trim());
        entity.setCode(code);
        entity.setModelCode(request.modelCode().trim());
        entity.setModelType(ModelType.CHAT);
        entity.setCapabilities(toJson(request.capabilities()));
        entity.setTemperature(request.temperature());
        entity.setTopP(request.topP());
        entity.setMaxTokens(request.maxTokens());
        entity.setContextWindow(request.contextWindow());
        entity.setStopSequences(toJson(request.stopSequences()));
        entity.setTimeoutSeconds(request.timeoutSeconds());
        entity.setMaxRetries(request.maxRetries());
        entity.setStatus(request.status());
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(now);
        if (id == null) {
            entity.setCreatedBy(operatorId);
            entity.setCreatedAt(now);
            entity.setIsDefault(false);
            entity.setLastTestStatus(UNTESTED);
        }

        instanceRepo.save(entity);
        auditService.record(
                "MODEL",
                "INSTANCE",
                entity.getId(),
                operatorId,
                id == null ? "MODEL_CREATE" : "MODEL_UPDATE",
                "模型实例=" + code);
        return modelConverter.toInstanceVO(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        ModelInstanceEntity entity = requireInstance(id);
        if (Boolean.TRUE.equals(entity.getIsDefault()) && status == EnableStatus.DISABLED) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "默认模型不能直接停用，请先设置新的默认模型");
        }
        if (status == EnableStatus.ACTIVE) {
            requireAvailable(
                    requireProvider(entity.getProviderId()),
                    requireCredential(entity.getCredentialId()));
        }

        entity.setStatus(status);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(OffsetDateTime.now());
        instanceRepo.save(entity);
        auditService.record(
                "MODEL", "INSTANCE", id, operatorId, "MODEL_STATUS_CHANGE", "状态=" + status);
    }

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

        instanceRepo.clearDefault(entity.getModelType().name());
        entity.setIsDefault(true);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(OffsetDateTime.now());
        instanceRepo.save(entity);
        auditService.record(
                "MODEL", "INSTANCE", id, operatorId, "MODEL_SET_DEFAULT", "设置默认模型");
    }

    private static void requireAvailable(
            ModelProviderEntity provider, ModelCredentialEntity credential) {
        if (provider.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "所选供应商已停用");
        }
        if (credential.getStatus() != ModelCredentialStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "所选密钥不可用");
        }
    }

    private ModelProviderEntity requireProvider(Long id) {
        return providerRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型供应商不存在"));
    }

    private ModelCredentialEntity requireCredential(Long id) {
        return credentialRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型密钥不存在"));
    }

    private ModelInstanceEntity requireInstance(Long id) {
        return instanceRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型实例不存在"));
    }

    private static String toJson(List<String> values) {
        return JSON.toJSONString(values == null ? List.of() : values);
    }

}
