package com.lingxi.workbench.admin.menu.service;

import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.menu.model.vo.AdminMenuVO;

import java.util.List;
import java.util.Set;

/**
 * 管理端菜单和功能权限业务接口。
 */
public interface AdminMenuService {

    /** 查询管理页面使用的完整菜单树，包含停用节点和按钮节点。 */
    List<AdminMenuVO> listTree();

    /** 查询指定管理员可见的启用导航树，不返回按钮节点。 */
    List<AdminMenuVO> listGrantedTree(Long accountId);

    /** 查询指定管理员拥有的去重权限编码。 */
    List<String> listPermissionCodes(Long accountId);

    /** 校验一组菜单主键是否全部存在，供角色菜单分配使用。 */
    boolean existsAll(Set<Long> menuIds);

    /** 新增目录、页面菜单或按钮权限。 */
    AdminMenuVO create(CreateMenuRequest request);

    /** 修改菜单节点，菜单编码创建后保持不变。 */
    AdminMenuVO update(Long id, UpdateMenuRequest request);

    /** 删除没有子节点且未被角色引用的菜单。 */
    void delete(Long id);
}
