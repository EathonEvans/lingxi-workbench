package com.lingxi.workbench.admin.role.service;

import com.lingxi.workbench.admin.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.role.model.vo.AdminRoleVO;

import java.util.List;
import java.util.Set;

/**
 * 角色管理业务接口。
 */
public interface AdminRoleService {

    /** 查询角色列表。 */
    List<AdminRoleVO> list();

    /** 查询账号拥有的启用角色编码，供 Sa-Token 鉴权使用。 */
    List<String> listRoleCodes(Long accountId);

    /** 查询管理员当前已分配的角色主键。 */
    Set<Long> listAccountRoleIds(Long accountId);

    /** 查询角色当前显式分配的菜单主键；ROOT 角色返回空集合。 */
    Set<Long> listRoleMenuIds(Long roleId);

    /** 查询角色当前显式分配的数据范围部门主键。 */
    Set<Long> listRoleDepartmentIds(Long roleId);

    /** 新增角色。 */
    AdminRoleVO create(CreateRoleRequest request);

    /** 修改角色。 */
    AdminRoleVO update(Long id, UpdateRoleRequest request);

    /** 删除未分配给账号的非内置角色。 */
    void delete(Long id);

    /** 为管理员全量分配角色。 */
    void assignAccountRoles(Long accountId, Set<Long> roleIds);

    /** 为角色全量分配菜单和按钮权限。 */
    void assignMenus(Long roleId, Set<Long> menuIds);

    /** 为自定义数据范围角色全量分配部门。 */
    void assignDepartments(Long roleId, Set<Long> departmentIds);
}
