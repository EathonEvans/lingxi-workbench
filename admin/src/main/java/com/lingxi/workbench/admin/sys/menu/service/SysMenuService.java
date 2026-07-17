package com.lingxi.workbench.admin.sys.menu.service;

import com.lingxi.workbench.admin.sys.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.vo.SysMenuVO;

import java.util.List;
import java.util.Set;

/**
 * 管理端菜单和功能权限业务接口。
 */
public interface SysMenuService {

    /** 查询管理页面使用的完整菜单树，包含停用节点和按钮节点。 */
    List<SysMenuVO> listTree();

    /** 查询指定管理员可见的启用导航树，不返回按钮节点。 */
    List<SysMenuVO> listGrantedTree(Long userId);

    /** 查询指定管理员拥有的去重权限编码。 */
    List<String> listPermissionCodes(Long userId);

    /**
     * 校验角色菜单分配集合中的主键完整性和父子层级连续性。
     * 子节点被选中时，其父节点必须同时存在于集合中。
     */
    void validateAssignmentHierarchy(Set<Long> menuIds);

    /** 新增目录、页面菜单或按钮权限。 */
    SysMenuVO create(CreateMenuRequest request);

    /** 修改菜单节点，菜单编码创建后保持不变。 */
    SysMenuVO update(Long id, UpdateMenuRequest request);

    /** 删除没有子节点且未被角色引用的菜单。 */
    void delete(Long id);
}
