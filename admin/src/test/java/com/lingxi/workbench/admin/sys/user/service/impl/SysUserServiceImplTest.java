package com.lingxi.workbench.admin.sys.user.service.impl;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import com.lingxi.workbench.admin.file.service.AvatarUrlService;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.sys.user.model.vo.SysAvatarVO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 系统用户资料更新边界测试。 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    private static final String AVATAR_OBJECT_KEY =
            "avatars/7/5b6f3f30-e19e-4d02-81d7-5f044575147f.png";

    @Mock
    private SysUserRepo sysUserRepo;
    @Mock
    private SysUserConverter sysUserConverter;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private AuditService auditService;
    @Mock
    private AvatarUrlService avatarUrlService;

    @InjectMocks
    private SysUserServiceImpl service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldOnlyUpdateCurrentUserAvatar() {
        UserContext.set(new LoginUser(7L, "admin", "管理员", null));
        when(sysUserRepo.findById(7L)).thenReturn(Optional.of(user()));
        when(avatarUrlService.resolve(AVATAR_OBJECT_KEY))
                .thenReturn(new AvatarUrlService.AvatarUrl(
                        "https://example.com/avatar", AVATAR_OBJECT_KEY));

        SysAvatarVO result = service.updateCurrentAvatar(AVATAR_OBJECT_KEY);

        assertThat(result.avatarUrl()).isEqualTo("https://example.com/avatar");
        verify(sysUserRepo).updateAvatar(7L, AVATAR_OBJECT_KEY);
        verify(auditService).record(
                "SYSTEM", "USER", 7L, 7L, "USER_AVATAR_UPDATE", "修改个人头像");
    }

    private static SysUserBO user() {
        return new SysUserBO(
                7L, "admin", "password", "管理员", null, null, null, 1L,
                SysUserStatus.ACTIVE, true, true, null, null);
    }
}
