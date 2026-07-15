package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.department.model.bo.SysDataScopeBO;
import com.lingxi.workbench.admin.department.repo.SysDepartmentRepo;
import com.lingxi.workbench.admin.department.service.SysDataScopeService;
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

    @Override
    @Transactional(readOnly = true)
    public SysDataScopeBO resolve(Long userId) {
        if (sysDepartmentRepo.hasAllDataScope(userId)) {
            return new SysDataScopeBO(true, false, Set.of());
        }

        Set<Long> departmentIds = new HashSet<>(sysDepartmentRepo.findAccessibleDepartmentIds(userId));
        if (departmentIds.isEmpty()) {
            return new SysDataScopeBO(false, true, Set.of());
        }
        return new SysDataScopeBO(false, false, departmentIds);
    }
}
