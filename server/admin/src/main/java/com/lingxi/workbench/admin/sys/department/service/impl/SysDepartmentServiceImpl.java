package com.lingxi.workbench.admin.sys.department.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.sys.department.model.converter.SysDepartmentConverter;
import com.lingxi.workbench.admin.sys.department.model.entity.SysDepartmentEntity;
import com.lingxi.workbench.admin.sys.department.model.request.CreateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.request.UpdateDepartmentRequest;
import com.lingxi.workbench.admin.sys.department.model.vo.SysDepartmentVO;
import com.lingxi.workbench.admin.sys.department.repo.SysDepartmentRepo;
import com.lingxi.workbench.admin.sys.department.service.SysDepartmentService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * 查询完整部门树，按排序序号和主键升序排列子节点。
     *
     * @return 部门树根节点列表
     */
    @Override
    public List<SysDepartmentVO> listTree() {
        List<SysDepartmentEntity> departments = sysDepartmentRepo.findAll();
        Map<Long, List<SysDepartmentEntity>> childrenByParent = new HashMap<>();
        List<SysDepartmentEntity> roots = new ArrayList<>();
        for (SysDepartmentEntity department : departments) {
            if (Objects.isNull(department.getParentId())) {
                roots.add(department);
            } else {
                childrenByParent.computeIfAbsent(department.getParentId(), ignored -> new ArrayList<>())
                        .add(department);
            }
        }
        return roots.stream().map(root -> toTreeNode(root, childrenByParent)).toList();
    }

    /**
     * 校验一组部门主键是否全部存在，供角色数据范围分配使用。
     *
     * @param departmentIds 部门主键集合
     * @return 全部存在时返回 true
     */
    @Override
    public boolean existsAll(Set<Long> departmentIds) {
        return sysDepartmentRepo.existsAll(departmentIds);
    }

    /**
     * 新增部门，校验上级部门存在性、编码唯一性和父子关系合法性。
     *
     * @param request 新增部门请求
     * @return 新增后的部门视图
     */
    @Override
    @Transactional
    public SysDepartmentVO create(CreateDepartmentRequest request) {
        if (Objects.isNull(request.getParentId())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "新部门必须设置上级部门");
        }
        validateParent(request.getParentId(), null);
        if (sysDepartmentRepo.existsByCode(request.getCode())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "部门编码已存在");
        }
        SysDepartmentEntity entity = sysDepartmentConverter.toEntity(request);
        entity.setName(request.getName().trim());
        entity.setSortOrder(Objects.isNull(request.getSortOrder()) ? 0 : request.getSortOrder());
        sysDepartmentRepo.insert(entity);
        return sysDepartmentConverter.toVO(entity);
    }

    /**
     * 修改部门基础信息或调整上级部门，根部门不允许修改上级和状态。
     *
     * @param id      部门主键
     * @param request 修改请求
     * @return 修改后的部门视图
     */
    @Override
    @Transactional
    public SysDepartmentVO update(Long id, UpdateDepartmentRequest request) {
        SysDepartmentEntity entity = requireDepartment(id);
        validateRootAndParentConstraint(entity, request);
        validateParent(request.parentId(), id);
        sysDepartmentConverter.updateEntity(request, entity);
        entity.setName(request.name().trim());
        entity.setSortOrder(Objects.isNull(request.sortOrder()) ? 0 : request.sortOrder());
        sysDepartmentRepo.update(entity);
        return sysDepartmentConverter.toVO(entity);
    }

    /**
     * 删除部门，需确保无子部门、无管理员归属且无角色引用。
     *
     * @param id 部门主键
     */
    @Override
    @Transactional
    public void delete(Long id) {
        SysDepartmentEntity entity = requireDepartment(id);
        if (ROOT_DEPARTMENT_CODE.equals(entity.getCode())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "根部门是系统组织树的根节点，不能删除");
        }
        if (sysDepartmentRepo.existsChildren(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "请先删除或迁移当前部门的子部门");
        }
        if (sysDepartmentRepo.countUsers(id) > 0) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "部门下仍有管理员，不能删除");
        }
        if (sysDepartmentRepo.countRoleReferences(id) > 0) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "部门已被角色数据范围引用，不能删除");
        }
        sysDepartmentRepo.delete(id);
    }

    /**
     * 沿父链向上检查，防止更新父部门时形成 A -> B -> A 这样的闭环。
     */
    private void validateParent(Long parentId, Long currentId) {
        if (Objects.isNull(parentId)) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "部门不能将自己设置为父部门");
        }
        SysDepartmentEntity parent = requireDepartment(parentId);
        if (parent.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "不能将部门挂载到已停用部门下");
        }

        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (Objects.nonNull(cursor)) {
            if (!visited.add(cursor) || cursor.equals(currentId)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "部门父子关系不能形成循环");
            }
            cursor = requireDepartment(cursor).getParentId();
        }
    }

    private SysDepartmentEntity requireDepartment(Long id) {
        return sysDepartmentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "部门不存在"));
    }

    /**
     * 根部门必须保持启用且没有父节点，其他部门必须归属于组织树。
     */

    private static void validateRootAndParentConstraint(SysDepartmentEntity entity,
                                                        UpdateDepartmentRequest request) {
        if (ROOT_DEPARTMENT_CODE.equals(entity.getCode())) {
            if (Objects.nonNull(request.parentId()) || request.status() != EnableStatus.ACTIVE) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "根部门必须保持启用且不能设置上级部门");
            }
            return;
        }
        if (Objects.isNull(request.parentId())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "非根部门必须设置上级部门");
        }
    }

    /**
     * 递归构建部门树节点，将实体转换为视图并填充子部门列表。
     */
    private SysDepartmentVO toTreeNode(SysDepartmentEntity entity,
                                          Map<Long, List<SysDepartmentEntity>> childrenByParent) {
        SysDepartmentVO base = sysDepartmentConverter.toVO(entity);
        List<SysDepartmentVO> children = childrenByParent.getOrDefault(entity.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, childrenByParent))
                .toList();
        return SysDepartmentVO.builder()
                .id(base.getId()).parentId(base.getParentId()).name(base.getName())
                .code(base.getCode()).sortOrder(base.getSortOrder()).status(base.getStatus())
                .children(children).build();
    }
}
