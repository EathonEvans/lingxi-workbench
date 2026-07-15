package com.lingxi.workbench.admin.role.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.auth.service.SysUserService;
import com.lingxi.workbench.admin.department.service.SysDepartmentService;
import com.lingxi.workbench.admin.menu.service.SysMenuService;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import com.lingxi.workbench.admin.role.model.converter.SysRoleConverter;
import com.lingxi.workbench.admin.role.model.entity.SysRoleEntity;
import com.lingxi.workbench.admin.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.role.model.vo.SysRoleVO;
import com.lingxi.workbench.admin.role.repo.SysRoleRepo;
import com.lingxi.workbench.admin.role.service.SysRoleService;
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
public class SysRoleServiceImpl implements SysRoleService {

    private static final String ROOT_ROLE_CODE = "ROOT";

    private final SysRoleRepo sysRoleRepo;
    private final SysMenuService sysMenuService;
    private final SysDepartmentService sysDepartmentService;
    private final SysRoleConverter sysRoleConverter;
    private final SysUserService sysUserService;

    @Override
    public List<SysRoleVO> list() {
        return sysRoleRepo.findAll().stream().map(sysRoleConverter::toVO).toList();
    }

    @Override
    public List<String> listRoleCodes(Long userId) {
        return sysRoleRepo.findRoleCodesByUserId(userId);
    }

    @Override
    public Set<Long> listUserRoleIds(Long userId) {
        if (!sysRoleRepo.userExists(userId)) {
            throw new BusinessException(HttpStatusEnum.NOT_FOUND, "系统用户不存在");
        }
        return sysRoleRepo.findUserRoleIds(userId);
    }

    @Override
    public Set<Long> listRoleMenuIds(Long roleId) {
        requireRole(roleId);
        return sysRoleRepo.findRoleMenuIds(roleId);
    }

    @Override
    public Set<Long> listRoleDepartmentIds(Long roleId) {
        requireRole(roleId);
        return sysRoleRepo.findRoleDepartmentIds(roleId);
    }

    @Override
    @Transactional
    public SysRoleVO create(CreateRoleRequest request) {
        if (sysRoleRepo.existsByCode(request.code())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "角色编码已存在");
        }
        SysRoleEntity entity = sysRoleConverter.toEntity(request);
        entity.setName(request.name().trim());
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        sysRoleRepo.insert(entity);
        return sysRoleConverter.toVO(entity);
    }

    @Override
    @Transactional
    public SysRoleVO update(Long id, UpdateRoleRequest request) {
        SysRoleEntity entity = requireRole(id);
        if (Boolean.TRUE.equals(entity.getBuiltIn())
                && (request.dataScope() != RoleDataScope.ALL
                || request.status() != EnableStatus.ACTIVE)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "系统内置角色必须保持启用且拥有全部数据范围");
        }
        sysRoleConverter.updateEntity(request, entity);
        entity.setName(request.name().trim());
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        sysRoleRepo.update(entity);

        // 数据范围离开 CUSTOM 后，旧的部门关联已无业务含义，立即清理避免脏数据误导后续维护者。
        if (request.dataScope() != RoleDataScope.CUSTOM) {
            sysRoleRepo.replaceRoleDepartments(id, Set.of());
        }
        return sysRoleConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRoleEntity entity = requireRole(id);
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "系统内置角色不能删除");
        }
        if (sysRoleRepo.countRoleUsers(id) > 0) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "角色仍分配给管理员，不能删除");
        }
        // 数据库外键会级联删除角色菜单和角色部门关系。
        sysRoleRepo.delete(id);
    }

    @Override
    @Transactional
    public void assignUserRoles(Long userId, Set<Long> roleIds) {
        if (!sysRoleRepo.userExists(userId)) {
            throw new BusinessException(HttpStatusEnum.NOT_FOUND, "系统用户不存在");
        }
        if (!sysRoleRepo.existsAll(roleIds)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "待分配角色中存在无效角色");
        }

        // 至少保留一个 ROOT 管理员，防止误操作后整个管理端永久失去授权入口。
        // 锁定 ROOT 角色行，使多个管理员并发撤销 ROOT 时也只能串行执行检查。
        SysRoleEntity rootRole = sysRoleRepo.findByCodeForUpdate(ROOT_ROLE_CODE).orElse(null);
        boolean currentlyRoot = listRoleCodes(userId).contains(ROOT_ROLE_CODE);
        boolean remainsRoot = rootRole != null && roleIds.contains(rootRole.getId());
        if (currentlyRoot && !remainsRoot
                && sysRoleRepo.countOtherUsersByRoleCode(userId, ROOT_ROLE_CODE) == 0) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "系统至少需要保留一个 ROOT 管理员");
        }
        sysRoleRepo.replaceUserRoles(userId, roleIds);
        sysUserService.recordRoleAssignment(userId, roleIds);
    }

    @Override
    @Transactional
    public void assignMenus(Long roleId, Set<Long> menuIds) {
        SysRoleEntity role = requireRole(roleId);
        if (ROOT_ROLE_CODE.equals(role.getCode())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "ROOT 角色自动拥有全部菜单，无需显式分配");
        }
        sysMenuService.validateAssignmentHierarchy(menuIds);
        // 参数校验通过后按前端提交的完整集合全量覆盖，后端不擅自增删任何菜单主键。
        sysRoleRepo.replaceRoleMenus(roleId, menuIds);
    }

    @Override
    @Transactional
    public void assignDepartments(Long roleId, Set<Long> departmentIds) {
        SysRoleEntity role = requireRole(roleId);
        if (role.getDataScope() != RoleDataScope.CUSTOM) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "只有自定义数据范围角色可以分配部门");
        }
        if (!sysDepartmentService.existsAll(departmentIds)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "待分配部门中存在无效部门");
        }
        sysRoleRepo.replaceRoleDepartments(roleId, departmentIds);
    }

    private SysRoleEntity requireRole(Long id) {
        return sysRoleRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "角色不存在"));
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
