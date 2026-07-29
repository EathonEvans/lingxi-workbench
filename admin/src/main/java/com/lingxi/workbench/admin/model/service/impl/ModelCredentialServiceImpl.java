package com.lingxi.workbench.admin.model.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.model.converter.ModelConverter;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.CreateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.request.ModelCredentialPageQuery;
import com.lingxi.workbench.admin.model.model.request.RotateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelCredentialVO;
import com.lingxi.workbench.admin.model.repo.ModelCredentialRepo;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.model.repo.ModelProviderRepo;
import com.lingxi.workbench.admin.model.service.ModelCredentialService;
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

/** 模型访问密钥业务实现。 */
@Service
@RequiredArgsConstructor
public class ModelCredentialServiceImpl implements ModelCredentialService {

    private final ModelProviderRepo providerRepo;
    private final ModelCredentialRepo credentialRepo;
    private final ModelInstanceRepo instanceRepo;
    private final AuditService auditService;
    private final SecretCipher cipher;
    private final ModelConverter modelConverter;

    /**
     * 按名称、供应商和状态分页查询模型访问密钥。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ModelCredentialVO> page(ModelCredentialPageQuery query) {
        return credentialRepo.findPage(query).map(modelConverter::toCredentialVO);
    }

    /**
     * 查询全部密钥选项，供模型实例下拉选择。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ModelCredentialVO> options() {
        return credentialRepo.findAll().stream().map(modelConverter::toCredentialVO).toList();
    }

    /**
     * 创建模型访问密钥。
     *
     * <p>校验供应商可用且同供应商下名称唯一，加密存储 API Key 明文。</p>
     */
    @Override
    @Transactional
    public ModelCredentialVO create(CreateModelCredentialRequest request, Long operatorId) {
        ModelProviderEntity provider = requireProvider(request.providerId());
        // 停用供应商不允许创建新密钥
        if (provider.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "停用的供应商不能新增密钥");
        }

        String name = request.name().trim();
        // 同一供应商下密钥名称唯一
        if (credentialRepo.existsByProviderIdAndName(request.providerId(), name)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "同一供应商下的密钥名称不能重复");
        }

        OffsetDateTime now = OffsetDateTime.now();
        // 加密存储 API Key 明文
        SecretCipher.EncryptedSecret encrypted = cipher.encrypt(request.apiKey().trim());
        ModelCredentialEntity entity = new ModelCredentialEntity();
        entity.setProviderId(request.providerId());
        entity.setName(name);
        entity.setSecretCiphertext(encrypted.ciphertext());
        entity.setSecretIv(encrypted.iv());
        entity.setSecretHint(secretHint(request.apiKey()));
        entity.setStatus(ModelCredentialStatus.ACTIVE);
        entity.setRotatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);

        credentialRepo.save(entity);
        auditService.record("MODEL", "CREDENTIAL", entity.getId(), operatorId,
                "CREDENTIAL_CREATE", "创建密钥=" + name);
        return modelConverter.toCredentialVO(entity);
    }

    /**
     * 使用新 API Key 替换原有密钥，旧值不保留。
     *
     * <p>已撤销密钥不可轮换。</p>
     */
    @Override
    @Transactional
    public void rotate(Long id, RotateModelCredentialRequest request, Long operatorId) {
        ModelCredentialEntity entity = requireCredential(id);
        // 已撤销密钥不可轮换
        if (entity.getStatus() == ModelCredentialStatus.REVOKED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已撤销密钥不能轮换");
        }

        OffsetDateTime now = OffsetDateTime.now();
        // 使用新明文重新加密存储
        SecretCipher.EncryptedSecret encrypted = cipher.encrypt(request.apiKey().trim());
        entity.setSecretCiphertext(encrypted.ciphertext());
        entity.setSecretIv(encrypted.iv());
        entity.setSecretHint(secretHint(request.apiKey()));
        entity.setRotatedAt(now);
        entity.setUpdatedBy(operatorId);

        credentialRepo.save(entity);
        auditService.record("MODEL", "CREDENTIAL", id, operatorId,
                "CREDENTIAL_ROTATE", "轮换密钥=" + entity.getName());
    }

    /**
     * 启用、停用或永久撤销模型访问密钥。
     *
     * <p>停用前需确保无活跃模型实例引用，撤销前需确保无任何模型实例引用。
     * 撤销时同时清除密文，不可恢复。</p>
     */
    @Override
    @Transactional
    public void changeStatus(Long id, ModelCredentialStatus status, Long operatorId) {
        ModelCredentialEntity entity = requireCredential(id);
        // 已撤销密钥不可再次变更状态
        if (entity.getStatus() == ModelCredentialStatus.REVOKED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已撤销密钥不能再次变更状态");
        }
        // 禁用前检查是否仍有启用中的模型实例引用
        if (status == ModelCredentialStatus.DISABLED
                && instanceRepo.existsActiveByCredentialId(id)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "密钥仍被启用模型使用，请先停用相关模型实例");
        }
        // 撤销前检查是否仍有任何模型实例引用
        if (status == ModelCredentialStatus.REVOKED
                && instanceRepo.existsByCredentialId(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "密钥仍被模型实例引用，不能撤销");
        }

        entity.setStatus(status);
        // 撤销时清除密文，不可恢复
        if (status == ModelCredentialStatus.REVOKED) {
            entity.setSecretCiphertext(null);
            entity.setSecretIv(null);
        }
        entity.setUpdatedBy(operatorId);
        credentialRepo.save(entity);
        auditService.record("MODEL", "CREDENTIAL", id, operatorId,
                "CREDENTIAL_STATUS_CHANGE", "状态=" + status);
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

    /** 生成密钥脱敏提示，格式为 "••••XXXX" 显示末尾 4 位。 */
    private static String secretHint(String value) {
        String secret = value.trim();
        return "••••" + secret.substring(Math.max(0, secret.length() - 4));
    }
}
