package com.lingxi.workbench.admin.model.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.model.converter.ModelConverter;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.request.ModelProviderPageQuery;
import com.lingxi.workbench.admin.model.model.request.SaveModelProviderRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelProviderVO;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.model.repo.ModelProviderRepo;
import com.lingxi.workbench.admin.model.service.ModelProviderService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** 模型供应商业务实现。 */
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl implements ModelProviderService {

    private static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private final ModelProviderRepo providerRepo;
    private final ModelInstanceRepo instanceRepo;
    private final AuditService auditService;
    private final ModelConverter modelConverter;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ModelProviderVO> page(ModelProviderPageQuery query) {
        return providerRepo.findPage(query).map(modelConverter::toProviderVO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelProviderVO> options() {
        return providerRepo.findAll().stream().map(modelConverter::toProviderVO).toList();
    }

    @Override
    @Transactional
    public ModelProviderVO save(
            Long id, SaveModelProviderRequest request, Long operatorId) {
        String code = request.code().trim().toLowerCase();
        if (providerRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "供应商编码已存在");
        }

        ModelProviderEntity entity = Objects.isNull(id) ? new ModelProviderEntity() : requireProvider(id);
        entity.setName(request.name().trim());
        entity.setCode(code);
        entity.setProtocol(OPENAI_COMPATIBLE);
        entity.setBaseUrl(normalizeUrl(request.baseUrl()));
        entity.setStatus(request.status());
        entity.setRemark(trimToNull(request.remark()));
        entity.setUpdatedBy(operatorId);
        if (Objects.isNull(id)) {
            entity.setCreatedBy(operatorId);
        }

        providerRepo.save(entity);
        auditService.record(
                "MODEL",
                "PROVIDER",
                entity.getId(),
                operatorId,
                Objects.isNull(id) ? "PROVIDER_CREATE" : "PROVIDER_UPDATE",
                "供应商=" + code);
        return modelConverter.toProviderVO(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        ModelProviderEntity entity = requireProvider(id);
        if (status == EnableStatus.DISABLED && instanceRepo.existsActiveByProviderId(id)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "供应商仍有关联的启用模型，请先停用相关模型实例");
        }

        entity.setStatus(status);
        entity.setUpdatedBy(operatorId);
        providerRepo.save(entity);
        auditService.record(
                "MODEL", "PROVIDER", id, operatorId, "PROVIDER_STATUS_CHANGE", "状态=" + status);
    }

    private ModelProviderEntity requireProvider(Long id) {
        return providerRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型供应商不存在"));
    }

    private static String normalizeUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            boolean invalidScheme = !List.of("http", "https").contains(uri.getScheme());
            boolean containsUnsafePart = uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null;
            if (invalidScheme || containsUnsafePart) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Base URL 格式不合法");
            }
            String normalized = uri.toString();
            return normalized.endsWith("/")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Base URL 格式不合法");
        }
    }

    private static String trimToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
