package com.lingxi.workbench.admin.knowledge.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeBaseRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeBaseService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 知识库管理业务实现。 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final KnowledgeBaseRepo baseRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeConverter converter;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query) {
        return baseRepo.findPage(query).map(converter::toBaseVO);
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeBaseVO detail(Long id) {
        return converter.toBaseVO(requireBase(id));
    }

    @Override
    @Transactional
    public KnowledgeBaseVO save(Long id, SaveKnowledgeBaseRequest request, Long operatorId) {
        String code = request.code().trim().toLowerCase();
        if (baseRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库编码已存在");
        }
        KnowledgeBaseEntity entity = Objects.isNull(id) ? new KnowledgeBaseEntity() : requireBase(id);
        entity.setName(request.name().trim());
        entity.setCode(code);
        entity.setDescription(CharSequenceUtil.isBlank(request.description()) ? null : request.description().trim());
        entity.setStatus(request.status());
        entity.setUpdatedBy(operatorId);
        if (Objects.isNull(id)) entity.setCreatedBy(operatorId);
        baseRepo.save(entity);
        auditService.record("KNOWLEDGE", "BASE", entity.getId(), operatorId,
                Objects.isNull(id) ? "KNOWLEDGE_CREATE" : "KNOWLEDGE_UPDATE", "知识库=" + code);
        return converter.toBaseVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        KnowledgeBaseEntity entity = requireBase(id);
        if (documentRepo.existsByBaseId(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库中仍有文档，不能删除");
        }
        baseRepo.delete(id);
        auditService.record("KNOWLEDGE", "BASE", id, operatorId, "KNOWLEDGE_DELETE", "知识库=" + entity.getCode());
    }

    private KnowledgeBaseEntity requireBase(Long id) {
        return baseRepo.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
    }
}
