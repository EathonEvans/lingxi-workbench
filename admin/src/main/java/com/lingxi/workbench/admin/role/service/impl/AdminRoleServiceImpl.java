package com.lingxi.workbench.admin.role.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.department.service.AdminDepartmentService;
import com.lingxi.workbench.admin.menu.service.AdminMenuService;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import com.lingxi.workbench.admin.role.model.converter.AdminRoleConverter;
import com.lingxi.workbench.admin.role.model.entity.AdminRoleEntity;
import com.lingxi.workbench.admin.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.role.model.vo.AdminRoleVO;
import com.lingxi.workbench.admin.role.repo.AdminRoleRepo;
import com.lingxi.workbench.admin.role.service.AdminRoleService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 角色管理实现。
 *
 * <p>角色是菜单权限与部门数据范围的聚合点。所有关联关系均采用全量替换语义，
 * 这样前端提交的勾选状态就是最终状态，接口行为明确且容易审计。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    private static final String ROOT_ROLE_CODE = "ROOT";

    private final AdminRoleRepo adminRoleRepo;
    private final AdminMenuService adminMenuService;
    private final AdminDepartmentService adminDepartmentService;
    private final AdminRoleConverter adminRoleConverter;

    @Override
    public List<AdminRoleVO> list() {
        return adminRoleRepo.findAll().stream().map(adminRoleConverter::toVO).toList();
    }

    @Override
    public List<String> listRoleCodes(Long accountId) {
        return adminRoleRepo.findRoleCodesByAccountId(accountId);
    }

    @Override
    public Set<Long> listAccountRoleIds(Long accountId) {
        if (!adminRoleRepo.accountExists(accountId)) {
            throw new BusinessException(404, 404, "管理员账号不存在");
        }
        return adminRoleRepo.findAccountRoleIds(accountId);
    }

    @Override
    public Set<Long> listRoleMenuIds(Long roleId) {
        requireRole(roleId);
        return adminRoleRepo.findRoleMenuIds(roleId);
    }

    @Override
    public Set<Long> listRoleDepartmentIds(Long roleId) {
        requireRole(roleId);
        return adminRoleRepo.findRoleDepartmentIds(roleId);
    }

    @Override
    @Transactional
    public AdminRoleVO create(CreateRoleRequest request) {
        if (adminRoleRepo.existsByCode(request.code())) {
            throw new BusinessException(409, 409, "角色编码已存在");
        }
        AdminRoleEntity entity = adminRoleConverter.toEntity(request);
        entity.setName(request.name().trim());
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        adminRoleRepo.insert(entity);
        return adminRoleConverter.toVO(entity);
    }

    @Override
    @Transactional
    public AdminRoleVO update(Long id, UpdateRoleRequest request) {
        AdminRoleEntity entity = requireRole(id);
        if (Boolean.TRUE.equals(entity.getBuiltIn())
                && (request.dataScope() != RoleDataScope.ALL
                || request.status() != EnableStatus.ACTIVE)) {
            throw new BusinessException(409, 409, "系统内置角色必须保持启用且拥有全部数据范围");
        }
        adminRoleConverter.updateEntity(request, entity);
        entity.setName(request.name().trim());
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        adminRoleRepo.update(entity);

        // 数据范围离开 CUSTOM 后，旧的部门关联已无业务含义，立即清理避免脏数据误导后续维护者。
        if (request.dataScope() != RoleDataScope.CUSTOM) {
            adminRoleRepo.replaceRoleDepartments(id, Set.of());
        }
        return adminRoleConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminRoleEntity entity = requireRole(id);
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(409, 409, "系统内置角色不能删除");
        }
        if (adminRoleRepo.countRoleAccounts(id) > 0) {
            throw new BusinessException(409, 409, "角色仍分配给管理员，不能删除");
        }
        // 数据库外键会级联删除角色菜单和角色部门关系。
        adminRoleRepo.delete(id);
    }

    @Override
    @Transactional
    public void assignAccountRoles(Long accountId, Set<Long> roleIds) {
        if (!adminRoleRepo.accountExists(accountId)) {
            throw new BusinessException(404, 404, "管理员账号不存在");
        }
        if (!adminRoleRepo.existsAll(roleIds)) {
            throw new BusinessException(400, 400, "待分配角色中存在无效角色");
        }

        // 至少保留一个 ROOT 管理员，防止误操作后整个管理端永久失去授权入口。
        // 锁定 ROOT 角色行，使多个管理员并发撤销 ROOT 时也只能串行执行检查。
        AdminRoleEntity rootRole = adminRoleRepo.findByCodeForUpdate(ROOT_ROLE_CODE).orElse(null);
        boolean currentlyRoot = listRoleCodes(accountId).contains(ROOT_ROLE_CODE);
        boolean remainsRoot = rootRole != null && roleIds.contains(rootRole.getId());
        if (currentlyRoot && !remainsRoot
                && adminRoleRepo.countOtherAccountsByRoleCode(accountId, ROOT_ROLE_CODE) == 0) {
            throw new BusinessException(409, 409, "系统至少需要保留一个 ROOT 管理员");
        }
        adminRoleRepo.replaceAccountRoles(accountId, roleIds);
    }

    @Override
    @Transactional
    public void assignMenus(Long roleId, Set<Long> menuIds) {
        AdminRoleEntity role = requireRole(roleId);
        if (ROOT_ROLE_CODE.equals(role.getCode())) {
            throw new BusinessException(409, 409, "ROOT 角色自动拥有全部菜单，无需显式分配");
        }
        if (!adminMenuService.existsAll(menuIds)) {
            throw new BusinessException(400, 400, "待分配菜单中存在无效菜单");
        }
        adminRoleRepo.replaceRoleMenus(roleId, menuIds);
    }

    @Override
    @Transactional
    public void assignDepartments(Long roleId, Set<Long> departmentIds) {
        AdminRoleEntity role = requireRole(roleId);
        if (role.getDataScope() != RoleDataScope.CUSTOM) {
            throw new BusinessException(400, 400, "只有自定义数据范围角色可以分配部门");
        }
        if (!adminDepartmentService.existsAll(departmentIds)) {
            throw new BusinessException(400, 400, "待分配部门中存在无效部门");
        }
        adminRoleRepo.replaceRoleDepartments(roleId, departmentIds);
    }

    private AdminRoleEntity requireRole(Long id) {
        return adminRoleRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, 404, "角色不存在"));
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
