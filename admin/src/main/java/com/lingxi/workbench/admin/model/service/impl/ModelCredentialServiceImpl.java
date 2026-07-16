package com.lingxi.workbench.admin.model.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.crypto.ModelCredentialCipher;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.CreateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.request.RotateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelCredentialVO;
import com.lingxi.workbench.admin.model.repo.ModelConfigRepo;
import com.lingxi.workbench.admin.model.service.ModelCredentialService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** 模型访问密钥业务实现。 */
@Service
@RequiredArgsConstructor
public class ModelCredentialServiceImpl implements ModelCredentialService {

    private final ModelConfigRepo repo;
    private final ModelCredentialCipher cipher;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ModelCredentialVO> page(PageQuery query) {
        return repo.credentialPage(query).map(this::toVO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelCredentialVO> options() {
        return repo.credentials().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public ModelCredentialVO create(CreateModelCredentialRequest request, Long operatorId) {
        ModelProviderEntity provider = requireProvider(request.providerId());
        if (provider.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "停用的供应商不能新增密钥");
        }

        String name = request.name().trim();
        if (repo.credentialNameExists(request.providerId(), name)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "同一供应商下的密钥名称不能重复");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ModelCredentialCipher.EncryptedSecret encrypted = cipher.encrypt(request.apiKey().trim());
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
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        repo.saveCredential(entity);
        repo.audit("CREDENTIAL", entity.getId(), operatorId,
                "CREDENTIAL_CREATE", "创建密钥=" + name);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void rotate(Long id, RotateModelCredentialRequest request, Long operatorId) {
        ModelCredentialEntity entity = requireCredential(id);
        if (entity.getStatus() == ModelCredentialStatus.REVOKED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已撤销密钥不能轮换");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ModelCredentialCipher.EncryptedSecret encrypted = cipher.encrypt(request.apiKey().trim());
        entity.setSecretCiphertext(encrypted.ciphertext());
        entity.setSecretIv(encrypted.iv());
        entity.setSecretHint(secretHint(request.apiKey()));
        entity.setRotatedAt(now);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(now);

        repo.saveCredential(entity);
        repo.audit("CREDENTIAL", id, operatorId,
                "CREDENTIAL_ROTATE", "轮换密钥=" + entity.getName());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, ModelCredentialStatus status, Long operatorId) {
        ModelCredentialEntity entity = requireCredential(id);
        if (entity.getStatus() == ModelCredentialStatus.REVOKED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已撤销密钥不能再次变更状态");
        }
        if (status == ModelCredentialStatus.DISABLED && repo.credentialUsedByActiveInstance(id)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "密钥仍被启用模型使用，请先停用相关模型实例");
        }
        if (status == ModelCredentialStatus.REVOKED && repo.credentialReferenced(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "密钥仍被模型实例引用，不能撤销");
        }

        entity.setStatus(status);
        if (status == ModelCredentialStatus.REVOKED) {
            entity.setSecretCiphertext(null);
            entity.setSecretIv(null);
        }
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(OffsetDateTime.now());
        repo.saveCredential(entity);
        repo.audit("CREDENTIAL", id, operatorId,
                "CREDENTIAL_STATUS_CHANGE", "状态=" + status);
    }

    private ModelProviderEntity requireProvider(Long id) {
        return repo.provider(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型供应商不存在"));
    }

    private ModelCredentialEntity requireCredential(Long id) {
        return repo.credential(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型密钥不存在"));
    }

    private ModelCredentialVO toVO(ModelCredentialEntity entity) {
        return new ModelCredentialVO(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getProviderId()),
                entity.getName(),
                entity.getSecretHint(),
                entity.getStatus(),
                entity.getRotatedAt(),
                entity.getUpdatedAt());
    }

    private static String secretHint(String value) {
        String secret = value.trim();
        return "••••" + secret.substring(Math.max(0, secret.length() - 4));
    }
}
