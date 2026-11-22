package com.lingxi.workbench.admin.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.file.service.AvatarUrlService;
import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.login.LoginFailureGuard;
import com.lingxi.workbench.framework.auth.password.BCryptPasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysLoginServiceImplTest {

    @Mock
    private SysUserRepo sysUserRepo;

    @Mock
    private SysUserConverter sysUserConverter;

    @Mock
    private BCryptPasswordHasher passwordHasher;

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private AvatarUrlService avatarUrlService;

    @Mock
    private LoginFailureGuard loginFailureGuard;

    @Mock
    private SaTokenInfo tokenInfo;

    private SysLoginServiceImpl sysLoginService;

    @BeforeEach
    void setUp() {
        sysLoginService = new SysLoginServiceImpl(
                sysUserRepo,
                sysUserConverter,
                passwordHasher,
                stpLogicDefinition,
                avatarUrlService,
                loginFailureGuard
        );
    }

    @Test
    void shouldLoginActiveSysAndIssueSession() {
        SysUserBO user = user(SysUserStatus.ACTIVE, true);
        SysLoginVO.SysProfileVO profile = SysLoginVO.SysProfileVO.of(1L, "admin", "系统管理员", null, 1L);
        when(sysUserRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("admin-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);
        when(sysUserConverter.toProfileVO(user, null, null)).thenReturn(profile);

        SysLoginVO result = sysLoginService.login(
                new SysLoginRequest(" Admin ", "correct-password")
        );

        assertEquals("admin-token", result.tokenValue());
        assertEquals(1L, result.user().getId());
        assertEquals("admin", result.user().getUsername());
    }

    @Test
    void shouldHideWhetherSysExists() {
        when(sysUserRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> sysLoginService.login(new SysLoginRequest("missing", "password"))
        );

        verify(passwordHasher).timingSafeDummyMatch(eq("password"));
        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectInvalidPassword() {
        SysUserBO user = user(SysUserStatus.ACTIVE, true);
        when(sysUserRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> sysLoginService.login(new SysLoginRequest("admin", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectDisabledSysAfterPasswordVerification() {
        SysUserBO user = user(SysUserStatus.DISABLED, true);
        when(sysUserRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> sysLoginService.login(new SysLoginRequest("admin", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectUserWithoutAdminAccess() {
        SysUserBO user = user(SysUserStatus.ACTIVE, false);
        when(sysUserRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> sysLoginService.login(new SysLoginRequest("admin", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectLoginWhenLockedByFailureGuard() {
        when(loginFailureGuard.isLocked("admin", "admin")).thenReturn(true);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> sysLoginService.login(new SysLoginRequest("admin", "correct-password"))
        );

        assertEquals("登录失败次数过多，账号已被临时锁定，请稍后再试", exception.getMessage());
        verify(sysUserRepo, never()).findByUsername(anyString());
    }

    private static SysUserBO user(SysUserStatus status, boolean adminAccessEnabled) {
        return SysUserBO.builder()
                .id(1L)
                .username("admin")
                .passwordHash("$2a$12$password-hash")
                .displayName("系统管理员")
                .avatarUrl(null)
                .email(null)
                .mobile(null)
                .departmentId(1L)
                .status(status)
                .applicationAccessEnabled(false)
                .adminAccessEnabled(adminAccessEnabled)
                .createdAt(null)
                .updatedAt(null)
                .build();
    }
}
