package com.lingxi.workbench.admin.domain.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.model.converter.DomainConverter;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.model.request.CreateDomainRequest;
import com.lingxi.workbench.admin.domain.model.request.UpdateDomainRequest;
import com.lingxi.workbench.admin.domain.model.vo.DomainVO;
import com.lingxi.workbench.admin.domain.repo.DomainRepo;
import com.lingxi.workbench.admin.domain.service.DomainService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 领域目录业务实现，最多允许三级。 */
@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {
    private static final int MAX_DEPTH = 3;

    private final DomainRepo repo;
    private final DomainConverter converter;

    @Override
    @Transactional(readOnly = true)
    public List<DomainVO> listTree() {
        List<DomainEntity> domains = repo.findAll();
        Map<Long, List<DomainEntity>> childMap = new HashMap<>();
        List<DomainEntity> roots = new ArrayList<>();
        for (DomainEntity domain : domains) {
            if (Objects.isNull(domain.getParentId())) {
                roots.add(domain);
            } else {
                childMap.computeIfAbsent(domain.getParentId(), ignored -> new ArrayList<>()).add(domain);
            }
        }
        return roots.stream().map(root -> toTree(root, childMap)).toList();
    }

    @Override
    @Transactional
    public DomainVO create(CreateDomainRequest request, Long operatorId) {
        String code = request.getCode().trim();
        if (repo.existsByCode(code)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "领域编码已存在");
        }
        DomainEntity parent = Objects.isNull(request.getParentId()) ? null : requireActive(request.getParentId());
        int depth = Objects.isNull(parent) ? 1 : parent.getDepth() + 1;
        if (depth > MAX_DEPTH) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "领域目录最多支持三级");
        }
        DomainEntity entity = converter.toEntity(request);
        entity.setName(request.getName().trim());
        entity.setCode(code);
        entity.setDescription(trim(request.getDescription()));
        entity.setDepth(depth);
        entity.setRootId(Objects.isNull(parent) ? null : parent.getRootId());
        entity.setSortOrder(Objects.requireNonNullElse(request.getSortOrder(), 0));
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        repo.insert(entity);
        if (Objects.isNull(parent)) {
            entity.setRootId(entity.getId());
            repo.update(entity);
        }
        return converter.toVO(entity);
    }

    @Override
    @Transactional
    public DomainVO update(Long id, UpdateDomainRequest request, Long operatorId) {
        DomainEntity entity = require(id);
        if (!Objects.equals(entity.getParentId(), request.getParentId()) && repo.hasChildren(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "存在子领域时不能调整上级领域");
        }
        if (Objects.equals(id, request.getParentId())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "领域不能选择自身作为上级");
        }
        DomainEntity parent = Objects.isNull(request.getParentId()) ? null : requireActive(request.getParentId());
        int depth = Objects.isNull(parent) ? 1 : parent.getDepth() + 1;
        if (depth > MAX_DEPTH) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "领域目录最多支持三级");
        }
        converter.update(request, entity);
        entity.setName(request.getName().trim());
        entity.setDescription(trim(request.getDescription()));
        entity.setDepth(depth);
        entity.setRootId(Objects.isNull(parent) ? id : parent.getRootId());
        entity.setSortOrder(Objects.requireNonNullElse(request.getSortOrder(), 0));
        entity.setUpdatedBy(operatorId);
        repo.update(entity);
        return converter.toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        if (repo.hasChildren(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "请先删除或迁移子领域");
        }
        if (repo.countRefs(id) > 0) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "领域仍被Agent、意图树或Application引用");
        }
        repo.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public DomainEntity requireActive(Long id) {
        DomainEntity entity = require(id);
        if (entity.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "领域已停用");
        }
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public DomainEntity requireRoot(Long id) {
        DomainEntity entity = requireActive(id);
        if (entity.getDepth() != 1 || !Objects.equals(entity.getId(), entity.getRootId())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Application必须选择一级领域");
        }
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean belongsTo(Long domainId, Long rootId) {
        return Objects.nonNull(domainId) && Objects.equals(requireActive(domainId).getRootId(), rootId);
    }

    private DomainEntity require(Long id) {
        return repo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "领域不存在"));
    }

    private DomainVO toTree(DomainEntity entity, Map<Long, List<DomainEntity>> childMap) {
        DomainVO base = converter.toVO(entity);
        return DomainVO.builder()
                .id(base.getId()).parentId(base.getParentId()).rootId(base.getRootId())
                .name(base.getName()).code(base.getCode()).description(base.getDescription())
                .depth(base.getDepth()).sortOrder(base.getSortOrder()).status(base.getStatus())
                .children(childMap.getOrDefault(entity.getId(), List.of()).stream()
                        .map(child -> toTree(child, childMap)).toList())
                .build();
    }

    private static String trim(String value) {
        return Objects.isNull(value) || value.isBlank() ? null : value.trim();
    }
}
