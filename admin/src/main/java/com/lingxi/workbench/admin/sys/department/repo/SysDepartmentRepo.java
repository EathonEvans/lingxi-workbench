package com.lingxi.workbench.admin.sys.department.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.sys.department.model.entity.SysDepartmentEntity;
import com.lingxi.workbench.admin.sys.department.repo.mapper.SysDepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 部门数据访问仓库。
 */
@Repository
@RequiredArgsConstructor
public class SysDepartmentRepo {

    private final SysDepartmentMapper sysDepartmentMapper;

    /**
     * 查询全部部门，按排序序号和主键升序排列。
     *
     * @return 全部部门列表
     */
    public List<SysDepartmentEntity> findAll() {
        return sysDepartmentMapper.selectList(
                Wrappers.<SysDepartmentEntity>lambdaQuery()
                        .orderByAsc(SysDepartmentEntity::getSortOrder, SysDepartmentEntity::getId)
        );
    }

    /**
     * 按主键查询部门。
     *
     * @param id 部门主键
     * @return 部门实体，不存在时返回空
     */
    public Optional<SysDepartmentEntity> findById(Long id) {
        return Optional.ofNullable(sysDepartmentMapper.selectById(id));
    }

    /**
     * 校验部门编码是否已被占用。
     *
     * @param code 部门编码
     * @return 编码已存在时返回 true
     */
    public boolean existsByCode(String code) {
        return sysDepartmentMapper.exists(
                Wrappers.<SysDepartmentEntity>lambdaQuery().eq(SysDepartmentEntity::getCode, code)
        );
    }

    /**
     * 校验指定部门是否存在子部门。
     *
     * @param parentId 父部门主键
     * @return 存在子部门时返回 true
     */
    public boolean existsChildren(Long parentId) {
        return sysDepartmentMapper.exists(
                Wrappers.<SysDepartmentEntity>lambdaQuery().eq(SysDepartmentEntity::getParentId, parentId)
        );
    }

    /**
     * 校验一批部门主键是否全部存在于数据库中。
     *
     * @param ids 部门主键集合，为空时视为全部存在
     * @return 全部存在时返回 true
     */
    public boolean existsAll(Set<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return true;
        }
        return sysDepartmentMapper.selectCount(
                Wrappers.<SysDepartmentEntity>lambdaQuery().in(SysDepartmentEntity::getId, ids)
        ) == ids.size();
    }

    /**
     * 统计归属于指定部门的管理员数量。
     *
     * @param departmentId 部门主键
     * @return 管理员数量
     */
    public long countUsers(Long departmentId) {
        return sysDepartmentMapper.countUsers(departmentId);
    }

    /**
     * 统计引用指定部门的角色数量。
     *
     * @param departmentId 部门主键
     * @return 引用次数
     */
    public long countRoleReferences(Long departmentId) {
        return sysDepartmentMapper.countRoleReferences(departmentId);
    }

    /**
     * 判断管理员是否通过任一启用角色拥有全公司数据权限。
     *
     * @param userId 管理员主键
     * @return 拥有全公司数据权限时返回 true
     */
    public boolean hasAllDataScope(Long userId) {
        return sysDepartmentMapper.hasAllDataScope(userId);
    }

    /**
     * 查询管理员可访问的部门主键集合。
     *
     * @param userId 管理员主键
     * @return 可访问的部门主键集合
     */
    public Set<Long> findAccessibleDepartmentIds(Long userId) {
        return Set.copyOf(sysDepartmentMapper.selectAccessibleDepartmentIds(userId));
    }

    /**
     * 新增部门。
     *
     * @param entity 部门实体
     */
    public void insert(SysDepartmentEntity entity) {
        sysDepartmentMapper.insert(entity);
    }

    /**
     * 按主键更新部门。
     *
     * @param entity 部门实体
     */
    public void update(SysDepartmentEntity entity) {
        sysDepartmentMapper.updateById(entity);
    }

    /**
     * 按主键删除部门。
     *
     * @param id 部门主键
     */
    public void delete(Long id) {
        sysDepartmentMapper.deleteById(id);
    }
}
