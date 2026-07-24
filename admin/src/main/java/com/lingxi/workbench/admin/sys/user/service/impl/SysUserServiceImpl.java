package com.lingxi.workbench.admin.sys.user.service.impl;

import com.lingxi.workbench.admin.file.common.enums.FileBasePath;
import com.lingxi.workbench.admin.file.service.AvatarUrlService;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.sys.user.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.sys.user.model.request.CreateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.sys.user.model.request.UpdateSysUserRequest;
import com.lingxi.workbench.admin.sys.user.model.vo.SysAvatarVO;
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
    private final AvatarUrlService avatarUrlService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<SysUserVO> page(SysUserPageQuery query) {
        normalizeQuery(query);
        return sysUserRepo.findPage(query).map(this::toUserView);
    }

    @Override
    @Transactional
    public SysUserVO create(CreateSysUserRequest request) {
        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());
        String mobile = normalizeNullable(request.getMobile());
        if (sysUserRepo.existsByUsername(username)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "用户名已存在");
        }
        validateContactUniqueness(email, mobile, null);

        SysUserEntity entity = new SysUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHasher.hash(request.getPassword()));
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setAvatarUrl(normalizeNullable(request.getAvatarUrl()));
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setDepartmentId(request.getDepartmentId());
        entity.setStatus(request.getStatus());
        sysUserRepo.insert(entity);
        Long operatorId = UserContext.userId();
        sysUserRepo.saveApplicationAccess(entity.getId(), request.getApplicationAccessEnabled(), operatorId);
        auditService.record("SYSTEM", "USER", entity.getId(), operatorId, "USER_CREATE",
                "创建账号，应用端访问=" + request.getApplicationAccessEnabled());
        return requireUserView(entity.getId());
    }

    @Override
    @Transactional
    public SysUserVO update(Long id, UpdateSysUserRequest request) {
        SysUserBO before = requireUser(id);
        String email = normalizeEmail(request.getEmail());
        String mobile = normalizeNullable(request.getMobile());
        validateContactUniqueness(email, mobile, id);

        SysUserEntity entity = new SysUserEntity();
        entity.setId(before.getId());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setAvatarUrl(normalizeNullable(request.getAvatarUrl()));
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setDepartmentId(request.getDepartmentId());
        entity.setStatus(request.getStatus());
        sysUserRepo.update(entity);
        Long operatorId = UserContext.userId();
        sysUserRepo.saveApplicationAccess(id, request.getApplicationAccessEnabled(), operatorId);
        auditService.record("SYSTEM", "USER", id, operatorId, "USER_UPDATE",
                "账号状态=" + request.getStatus() + "，应用端访问=" + request.getApplicationAccessEnabled());
        return requireUserView(id);
    }

    @Override
    @Transactional
    public SysAvatarVO updateCurrentAvatar(String avatarObjectKey) {
        Long userId = UserContext.userId();
        requireUser(userId);
        String normalizedObjectKey = avatarObjectKey.trim();
        if (!FileBasePath.AVATAR.ownsAvatar(normalizedObjectKey, userId)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "头像文件不属于当前用户");
        }
        sysUserRepo.updateAvatar(userId, normalizedObjectKey);
        auditService.record(
                "SYSTEM", "USER", userId, userId, "USER_AVATAR_UPDATE", "修改个人头像");
        AvatarUrlService.AvatarUrl avatar = avatarUrlService.resolve(normalizedObjectKey);
        return new SysAvatarVO(avatar.url(), avatar.objectKey());
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
                .map(this::toUserView)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "用户账号不存在"));
    }

    /**
     * 将 VO 中的 objectKey 替换为带签名的临时访问 URL，确保私有读写 OSS 中的头像可以被前端渲染。
     *
     * <p>如果存储值是已过期的签名 URL（历史数据），直接透传不做二次签名。</p>
     */
    private SysUserVO toUserView(SysUserBO user) {
        AvatarUrlService.AvatarUrl avatar = avatarUrlService.resolve(user.getAvatarUrl());
        return sysUserConverter.toVO(user, avatar.url(), avatar.objectKey());
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
