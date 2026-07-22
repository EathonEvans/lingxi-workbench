package com.lingxi.workbench.admin.sys.menu.service;

import com.lingxi.workbench.admin.sys.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.vo.SysMenuVO;

import java.util.List;
import java.util.Set;

/**
 * 管理端菜单和功能权限业务接口。
 *
 * <p>负责菜单树的增删改查、管理员授权导航树查询以及角色菜单分配层级校验。</p>
 */
public interface SysMenuService {

    /**
     * 查询管理页面使用的完整菜单树，包含停用节点和按钮节点。
     *
     * @return 菜单树根节点列表
     */
    List<SysMenuVO> listTree();

    /**
     * 查询指定管理员可见的启用导航树，不返回按钮节点和隐藏节点。
     *
     * @param userId 管理员主键
     * @return 授权导航树
     */
    List<SysMenuVO> listGrantedTree(Long userId);

    /**
     * 查询指定管理员拥有的去重权限编码列表。
     *
     * @param userId 管理员主键
     * @return 权限编码列表
     */
    List<String> listPermissionCodes(Long userId);

    /**
     * 校验角色菜单分配集合中的主键有效性和父子层级连续性。
     * 子节点被选中时，其父节点必须同时存在于集合中。
     *
     * @param menuIds 菜单主键集合
     */
    void validateAssignmentHierarchy(Set<Long> menuIds);

    /**
     * 新增目录、页面菜单或按钮权限。
     *
     * @param request 新增菜单请求
     * @return 新增后的菜单视图
     */
    SysMenuVO create(CreateMenuRequest request);

    /**
     * 修改菜单节点，菜单编码创建后保持不变。
     *
     * @param id      菜单主键
     * @param request 修改请求
     * @return 修改后的菜单视图
     */
    SysMenuVO update(Long id, UpdateMenuRequest request);

    /**
     * 删除菜单，需确保无子节点且未被角色引用。
     *
     * @param id 菜单主键
     */
    void delete(Long id);
}
