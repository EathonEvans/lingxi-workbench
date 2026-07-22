package com.lingxi.workbench.admin.sys.user.service.impl;

import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.sys.user.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.sys.user.model.request.CreateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.sys.user.model.request.UpdateSysUserRequest;
import com.lingxi.workbench.admin.sys.user.model.vo.SysUserVO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.admin.sys.user.service.SysUserService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/** 系统用户目录实现。 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserRepo sysUserRepo;
    private final SysUserConverter sysUserConverter;
    private final PasswordHasher passwordHasher;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<SysUserVO> page(SysUserPageQuery query) {
        normalizeQuery(query);
        return sysUserRepo.findPage(query).map(sysUserConverter::toVO);
    }

    @Override
    @Transactional
    public SysUserVO create(CreateSysUserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String mobile = normalizeNullable(request.mobile());
        if (sysUserRepo.existsByUsername(username)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "用户名已存在");
        }
        validateContactUniqueness(email, mobile, null);

        SysUserEntity entity = new SysUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHasher.hash(request.password()));
        entity.setDisplayName(request.displayName().trim());
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setDepartmentId(request.departmentId());
        entity.setStatus(request.status());
        sysUserRepo.insert(entity);
        Long operatorId = UserContext.userId();
        sysUserRepo.saveApplicationAccess(entity.getId(), request.applicationAccessEnabled(), operatorId);
        auditService.record("SYSTEM", "USER", entity.getId(), operatorId, "USER_CREATE",
                "创建账号，应用端访问=" + request.applicationAccessEnabled());
        return requireUserView(entity.getId());
    }

    @Override
    @Transactional
    public SysUserVO update(Long id, UpdateSysUserRequest request) {
        SysUserBO before = requireUser(id);
        String email = normalizeEmail(request.email());
        String mobile = normalizeNullable(request.mobile());
        validateContactUniqueness(email, mobile, id);

        SysUserEntity entity = new SysUserEntity();
        entity.setId(before.id());
        entity.setDisplayName(request.displayName().trim());
        entity.setAvatarUrl(normalizeNullable(request.avatarUrl()));
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setDepartmentId(request.departmentId());
        entity.setStatus(request.status());
        sysUserRepo.update(entity);
        Long operatorId = UserContext.userId();
        sysUserRepo.saveApplicationAccess(id, request.applicationAccessEnabled(), operatorId);
        auditService.record("SYSTEM", "USER", id, operatorId, "USER_UPDATE",
                "账号状态=" + request.status() + "，应用端访问=" + request.applicationAccessEnabled());
        return requireUserView(id);
    }

    @Override
    public void recordRoleAssignment(Long userId, Set<Long> roleIds) {
        requireUser(userId);
        auditService.record("SYSTEM", "USER", userId, UserContext.userId(), "SYS_ROLE_ASSIGNMENT",
                "管理端角色ID=" + roleIds.stream().sorted().toList());
    }

    private SysUserBO requireUser(Long id) {
        return sysUserRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "系统用户不存在"));
    }

    private SysUserVO requireUserView(Long id) {
        return sysUserRepo.findById(id)
                .map(sysUserConverter::toVO)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "用户账号不存在"));
    }

    private void validateContactUniqueness(String email, String mobile, Long excludeId) {
        if (sysUserRepo.existsByEmail(email, excludeId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "邮箱地址已被使用");
        }
        if (sysUserRepo.existsByMobile(mobile, excludeId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "手机号已被使用");
        }
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeEmail(String value) {
        String email = normalizeNullable(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private static void normalizeQuery(SysUserPageQuery query) {
        query.setUsername(normalizeNullable(query.getUsername()));
        query.setDisplayName(normalizeNullable(query.getDisplayName()));
    }
}
