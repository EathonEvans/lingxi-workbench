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
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundSecurityException;
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
    private final ModelOutboundAddressPolicy outboundAddressPolicy;

    /**
     * 按名称、编码和状态分页查询模型供应商。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ModelProviderVO> page(ModelProviderPageQuery query) {
        return providerRepo.findPage(query).map(modelConverter::toProviderVO);
    }

    /**
     * 查询全部供应商选项，供关联配置下拉选择。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ModelProviderVO> options() {
        return providerRepo.findAll().stream().map(modelConverter::toProviderVO).toList();
    }

    /**
     * 新增或修改模型供应商。
     *
     * <p>编码全局唯一且统一转为小写，Base URL 经过合法性与安全校验。</p>
     */
    @Override
    @Transactional
    public ModelProviderVO save(
            Long id, SaveModelProviderRequest request, Long operatorId) {
        String code = request.getCode().trim().toLowerCase();
        // 编码全局唯一，更新时排除自身
        if (providerRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "供应商编码已存在");
        }

        ModelProviderEntity entity = Objects.isNull(id) ? new ModelProviderEntity() : requireProvider(id);
        entity.setName(request.getName().trim());
        entity.setCode(code);
        // 当前仅支持 OpenAI 兼容协议
        entity.setProtocol(OPENAI_COMPATIBLE);
        entity.setBaseUrl(normalizeUrl(request.getBaseUrl()));
        entity.setStatus(request.getStatus());
        entity.setRemark(trimToNull(request.getRemark()));
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

    /**
     * 启用或停用模型供应商，停用前需确保无活跃模型实例。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        ModelProviderEntity entity = requireProvider(id);
        // 禁用前检查是否有关联的启用模型
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

    /** 查询供应商，不存在时抛出 404 异常。 */
    private ModelProviderEntity requireProvider(Long id) {
        return providerRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "模型供应商不存在"));
    }

    /**
     * 归一化 Base URL：校验协议仅支持 HTTP/HTTPS、出站地址安全，去除尾部斜杠，
     * 拒绝包含用户信息、查询参数和片段的地址。
     */
    private String normalizeUrl(String value) {
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
            // 私网、回环和链路本地地址默认拒绝；本地或内网部署模型需在服务端配置显式放行。
            outboundAddressPolicy.validateOutboundAddress(value);
            String normalized = uri.toString();
            return normalized.endsWith("/")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        } catch (ModelOutboundSecurityException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Base URL 格式不合法");
        }
    }

    /** 空白字符串转为 null，避免空字符串存入数据库。 */
    private static String trimToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
