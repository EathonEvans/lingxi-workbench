package com.lingxi.workbench.admin.sys.menu.repo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.sys.menu.model.entity.SysMenuEntity;
import com.lingxi.workbench.admin.sys.menu.repo.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 菜单数据访问仓库，统一封装树节点和授权菜单查询。
 */
@Repository
@RequiredArgsConstructor
public class SysMenuRepo {

    private final SysMenuMapper sysMenuMapper;

    /**
     * 查询菜单列表（上限 1000 防止字典表失控），按排序序号和主键升序排列。
     *
     * @return 菜单列表
     */
    public List<SysMenuEntity> findAll() {
        return sysMenuMapper.selectList(
                Wrappers.<SysMenuEntity>lambdaQuery()
                        .orderByAsc(SysMenuEntity::getSortOrder, SysMenuEntity::getId)
                        .last("LIMIT 1000")
        );
    }

    /**
     * 查询指定管理员拥有的全部生效菜单，包含递归补齐的父节点。
     *
     * @param userId 管理员主键
     * @return 授权菜单列表
     */
    public List<SysMenuEntity> findGrantedMenus(Long userId) {
        return sysMenuMapper.selectGrantedMenus(userId);
    }

    /**
     * 按主键查询菜单。
     *
     * @param id 菜单主键
     * @return 菜单实体，不存在时返回空
     */
    public Optional<SysMenuEntity> findById(Long id) {
        return Optional.ofNullable(sysMenuMapper.selectById(id));
    }

    /**
     * 按主键集合查询菜单，用于校验角色菜单分配参数。
     *
     * @param ids 菜单主键集合
     * @return 匹配的菜单列表
     */
    public List<SysMenuEntity> findByIds(Set<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return sysMenuMapper.selectByIds(ids);
    }

    /**
     * 校验菜单编码是否已被占用，可排除指定菜单用于更新场景。
     *
     * @param code      菜单编码
     * @param excludeId 排除的菜单主键，可为空
     * @return 编码已存在时返回 true
     */
    public boolean existsByCode(String code, Long excludeId) {
        return sysMenuMapper.exists(
                Wrappers.<SysMenuEntity>lambdaQuery()
                        .eq(SysMenuEntity::getCode, code)
                        .ne(Objects.nonNull(excludeId), SysMenuEntity::getId, excludeId)
        );
    }

    /**
     * 校验权限编码是否已被占用，可排除指定菜单。
     * 空权限编码不参与校验。
     *
     * @param permissionCode 权限编码
     * @param excludeId      排除的菜单主键，可为空
     * @return 权限编码已存在时返回 true
     */
    public boolean existsByPermissionCode(String permissionCode, Long excludeId) {
        if (CharSequenceUtil.isBlank(permissionCode)) {
            return false;
        }
        return sysMenuMapper.exists(
                Wrappers.<SysMenuEntity>lambdaQuery()
                        .eq(SysMenuEntity::getPermissionCode, permissionCode)
                        .ne(Objects.nonNull(excludeId), SysMenuEntity::getId, excludeId)
        );
    }

    /**
     * 校验指定菜单是否存在子节点。
     *
     * @param parentId 父菜单主键
     * @return 存在子节点时返回 true
     */
    public boolean existsChildren(Long parentId) {
        return sysMenuMapper.exists(
                Wrappers.<SysMenuEntity>lambdaQuery().eq(SysMenuEntity::getParentId, parentId)
        );
    }

    /**
     * 统计引用指定菜单的角色数量。
     *
     * @param menuId 菜单主键
     * @return 引用次数
     */
    public long countRoleReferences(Long menuId) {
        return sysMenuMapper.countRoleReferences(menuId);
    }

    /**
     * 新增菜单。
     *
     * @param entity 菜单实体
     */
    public void insert(SysMenuEntity entity) {
        sysMenuMapper.insert(entity);
    }

    /**
     * 按主键更新菜单。
     *
     * @param entity 菜单实体
     */
    public void update(SysMenuEntity entity) {
        sysMenuMapper.updateById(entity);
    }

    /**
     * 按主键删除菜单。
     *
     * @param id 菜单主键
     */
    public void delete(Long id) {
        sysMenuMapper.deleteById(id);
    }
}
