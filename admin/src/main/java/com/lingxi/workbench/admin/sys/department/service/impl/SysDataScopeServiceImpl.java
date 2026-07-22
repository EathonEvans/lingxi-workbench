package com.lingxi.workbench.admin.sys.department.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.lingxi.workbench.admin.sys.department.model.bo.SysDataScopeBO;
import com.lingxi.workbench.admin.sys.department.repo.SysDepartmentRepo;
import com.lingxi.workbench.admin.sys.department.service.SysDataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 部门数据权限计算实现。
 *
 * <p>多个角色按“权限取并集”合并：任一角色为 ALL 即拥有全量权限；否则合并各角色可访问部门；
 * 没有任何部门权限时按 SELF 处理，这是缺少角色或配置异常时更安全的默认值。</p>
 */
@Service
@RequiredArgsConstructor
public class SysDataScopeServiceImpl implements SysDataScopeService {

    private final SysDepartmentRepo sysDepartmentRepo;

    /**
     * 合并管理员全部启用角色的数据范围，按优先级逐层判断。
     *
     * <ol>
     *   <li>任一角色为 ALL 即拥有全量权限，直接返回全量范围；</li>
     *   <li>合并各角色的 DEPARTMENT、DEPARTMENT_AND_CHILDREN、CUSTOM 部门集合；</li>
     *   <li>合并后仍为空时，按 SELF 处理作为缺少角色时的安全默认值。</li>
     * </ol>
     *
     * @param userId 管理员主键
     * @return 数据范围计算结果
     */
    @Override
    @Transactional(readOnly = true)
    public SysDataScopeBO resolve(Long userId) {
        if (sysDepartmentRepo.hasAllDataScope(userId)) {
            return new SysDataScopeBO(true, false, Set.of());
        }

        Set<Long> departmentIds = new HashSet<>(sysDepartmentRepo.findAccessibleDepartmentIds(userId));
        if (CollUtil.isEmpty(departmentIds)) {
            return new SysDataScopeBO(false, true, Set.of());
        }
        return new SysDataScopeBO(false, false, departmentIds);
    }
}
