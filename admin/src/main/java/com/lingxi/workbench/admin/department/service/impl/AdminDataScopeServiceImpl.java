package com.lingxi.workbench.admin.department.service.impl;

import com.lingxi.workbench.admin.department.model.bo.AdminDataScopeBO;
import com.lingxi.workbench.admin.department.repo.AdminDepartmentRepo;
import com.lingxi.workbench.admin.department.service.AdminDataScopeService;
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
public class AdminDataScopeServiceImpl implements AdminDataScopeService {

    private final AdminDepartmentRepo adminDepartmentRepo;

    @Override
    @Transactional(readOnly = true)
    public AdminDataScopeBO resolve(Long accountId) {
        if (adminDepartmentRepo.hasAllDataScope(accountId)) {
            return new AdminDataScopeBO(true, false, Set.of());
        }

        Set<Long> departmentIds = new HashSet<>(adminDepartmentRepo.findAccessibleDepartmentIds(accountId));
        if (departmentIds.isEmpty()) {
            return new AdminDataScopeBO(false, true, Set.of());
        }
        return new AdminDataScopeBO(false, false, departmentIds);
    }
}
