package com.lingxi.workbench.admin.auth.service.impl;

import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.model.converter.AdminAccountConverter;
import com.lingxi.workbench.admin.auth.model.entity.AdminAccountEntity;
import com.lingxi.workbench.admin.auth.model.request.CreateAdminAccountRequest;
import com.lingxi.workbench.admin.auth.model.request.UpdateAdminAccountRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminAccountVO;
import com.lingxi.workbench.admin.auth.repo.AdminAccountRepo;
import com.lingxi.workbench.admin.auth.service.AdminAccountService;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** 后台管理员账号目录实现。 */
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AdminAccountRepo adminAccountRepo;
    private final AdminAccountConverter adminAccountConverter;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional(readOnly = true)
    public List<AdminAccountVO> list() {
        return adminAccountRepo.findAll().stream().map(adminAccountConverter::toVO).toList();
    }

    @Override
    @Transactional
    public AdminAccountVO create(CreateAdminAccountRequest request) {
        String username = normalizeUsername(request.username());
        if (adminAccountRepo.existsByUsername(username)) {
            throw new BusinessException(409, 409, "用户名已存在");
        }

        AdminAccountEntity entity = new AdminAccountEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHasher.hash(request.password()));
        entity.setDisplayName(request.displayName().trim());
        entity.setDepartmentId(request.departmentId());
        entity.setStatus(request.status().name());
        adminAccountRepo.insert(entity);
        return adminAccountConverter.toVO(requireAccount(entity.getId()));
    }

    @Override
    @Transactional
    public AdminAccountVO update(Long id, UpdateAdminAccountRequest request) {
        AdminAccountEntity entity = new AdminAccountEntity();
        entity.setId(requireAccount(id).id());
        entity.setDisplayName(request.displayName().trim());
        entity.setAvatarUrl(normalizeNullable(request.avatarUrl()));
        entity.setDepartmentId(request.departmentId());
        entity.setStatus(request.status().name());
        adminAccountRepo.update(entity);
        return adminAccountConverter.toVO(requireAccount(id));
    }

    private AdminAccountBO requireAccount(Long id) {
        return adminAccountRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, 404, "管理员账号不存在"));
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
