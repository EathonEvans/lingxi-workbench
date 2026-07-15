package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.department.model.converter.SysDepartmentConverter;
import com.lingxi.workbench.admin.department.model.entity.SysDepartmentEntity;
import com.lingxi.workbench.admin.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.department.model.vo.SysDepartmentVO;
import com.lingxi.workbench.admin.department.repo.SysDepartmentRepo;
import com.lingxi.workbench.admin.department.service.SysDepartmentService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门管理实现。
 *
 * <p>部门树既用于组织管理，也是后续知识库数据权限的组织边界，因此这里集中维护
 * 父子关系、根部门保护和引用完整性，Controller 不重复编写业务规则。</p>
 */
@Service
@RequiredArgsConstructor
public class SysDepartmentServiceImpl implements SysDepartmentService {

    private static final String ROOT_DEPARTMENT_CODE = "ROOT";

    private final SysDepartmentRepo sysDepartmentRepo;
    private final SysDepartmentConverter sysDepartmentConverter;

    @Override
    public List<SysDepartmentVO> listTree() {
        List<SysDepartmentEntity> departments = sysDepartmentRepo.findAll();
        Map<Long, List<SysDepartmentEntity>> childrenByParent = new HashMap<>();
        List<SysDepartmentEntity> roots = new ArrayList<>();
        for (SysDepartmentEntity department : departments) {
            if (department.getParentId() == null) {
                roots.add(department);
            } else {
                childrenByParent.computeIfAbsent(department.getParentId(), ignored -> new ArrayList<>())
                        .add(department);
            }
        }
        return roots.stream().map(root -> toTreeNode(root, childrenByParent)).toList();
    }

    @Override
    public boolean existsAll(Set<Long> departmentIds) {
        return sysDepartmentRepo.existsAll(departmentIds);
    }

    @Override
    @Transactional
    public SysDepartmentVO create(CreateDepartmentRequest request) {
        if (request.parentId() == null) {
            throw new BusinessException(400, 400, "新部门必须设置上级部门");
        }
        validateParent(request.parentId(), null);
        if (sysDepartmentRepo.existsByCode(request.code())) {
            throw new BusinessException(409, 409, "部门编码已存在");
        }
        SysDepartmentEntity entity = sysDepartmentConverter.toEntity(request);
        entity.setName(request.name().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        sysDepartmentRepo.insert(entity);
        return sysDepartmentConverter.toVO(entity);
    }

    @Override
    @Transactional
    public SysDepartmentVO update(Long id, UpdateDepartmentRequest request) {
        SysDepartmentEntity entity = requireDepartment(id);
        validateRootAndParentConstraint(entity, request);
        validateParent(request.parentId(), id);
        sysDepartmentConverter.updateEntity(request, entity);
        entity.setName(request.name().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        sysDepartmentRepo.update(entity);
        return sysDepartmentConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysDepartmentEntity entity = requireDepartment(id);
        if (ROOT_DEPARTMENT_CODE.equals(entity.getCode())) {
            throw new BusinessException(409, 409, "根部门是系统组织树的根节点，不能删除");
        }
        if (sysDepartmentRepo.existsChildren(id)) {
            throw new BusinessException(409, 409, "请先删除或迁移当前部门的子部门");
        }
        if (sysDepartmentRepo.countUsers(id) > 0) {
            throw new BusinessException(409, 409, "部门下仍有管理员，不能删除");
        }
        if (sysDepartmentRepo.countRoleReferences(id) > 0) {
            throw new BusinessException(409, 409, "部门已被角色数据范围引用，不能删除");
        }
        sysDepartmentRepo.delete(id);
    }

    /**
     * 沿父链向上检查，防止更新父部门时形成 A -> B -> A 这样的闭环。
     */
    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(400, 400, "部门不能将自己设置为父部门");
        }
        SysDepartmentEntity parent = requireDepartment(parentId);
        if (parent.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(400, 400, "不能将部门挂载到已停用部门下");
        }

        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (!visited.add(cursor) || cursor.equals(currentId)) {
                throw new BusinessException(400, 400, "部门父子关系不能形成循环");
            }
            cursor = requireDepartment(cursor).getParentId();
        }
    }

    private SysDepartmentEntity requireDepartment(Long id) {
        return sysDepartmentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, 404, "部门不存在"));
    }

    /** 根部门必须保持启用且没有父节点，其他部门必须归属于组织树。 */
    private static void validateRootAndParentConstraint(SysDepartmentEntity entity,
                                                        UpdateDepartmentRequest request) {
        if (ROOT_DEPARTMENT_CODE.equals(entity.getCode())) {
            if (request.parentId() != null || request.status() != EnableStatus.ACTIVE) {
                throw new BusinessException(409, 409, "根部门必须保持启用且不能设置上级部门");
            }
            return;
        }
        if (request.parentId() == null) {
            throw new BusinessException(400, 400, "非根部门必须设置上级部门");
        }
    }

    private SysDepartmentVO toTreeNode(SysDepartmentEntity entity,
                                          Map<Long, List<SysDepartmentEntity>> childrenByParent) {
        SysDepartmentVO base = sysDepartmentConverter.toVO(entity);
        List<SysDepartmentVO> children = childrenByParent.getOrDefault(entity.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, childrenByParent))
                .toList();
        return new SysDepartmentVO(base.id(), base.parentId(), base.name(), base.code(),
                base.sortOrder(), base.status(), children);
    }
}
