package com.lingxi.workbench.admin.sys.role.service;

import com.lingxi.workbench.admin.sys.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.sys.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.sys.role.model.vo.SysRoleVO;

import java.util.List;
import java.util.Set;

/**
 * 角色管理业务接口。
 */
public interface SysRoleService {

    /** 查询角色列表。 */
    List<SysRoleVO> list();

    /** 查询账号拥有的启用角色编码，供 Sa-Token 鉴权使用。 */
    List<String> listRoleCodes(Long userId);

    /** 查询管理员当前已分配的角色主键。 */
    Set<Long> listUserRoleIds(Long userId);

    /** 查询角色当前显式分配的菜单主键；ROOT 角色返回空集合。 */
    Set<Long> listRoleMenuIds(Long roleId);

    /** 查询角色当前显式分配的数据范围部门主键。 */
    Set<Long> listRoleDepartmentIds(Long roleId);

    /** 新增角色。 */
    SysRoleVO create(CreateRoleRequest request);

    /** 修改角色。 */
    SysRoleVO update(Long id, UpdateRoleRequest request);

    /** 删除未分配给账号的非内置角色。 */
    void delete(Long id);

    /** 为管理员全量分配角色。 */
    void assignUserRoles(Long userId, Set<Long> roleIds);

    /** 为角色全量分配菜单和按钮权限。 */
    void assignMenus(Long roleId, Set<Long> menuIds);

    /** 为自定义数据范围角色全量分配部门。 */
    void assignDepartments(Long roleId, Set<Long> departmentIds);
}
