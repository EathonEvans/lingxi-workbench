package com.lingxi.workbench.admin.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
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
    private SaTokenInfo tokenInfo;

    private SysLoginServiceImpl sysLoginService;

    @BeforeEach
    void setUp() {
        sysLoginService = new SysLoginServiceImpl(
                sysUserRepo,
                sysUserConverter,
                passwordHasher,
                stpLogicDefinition
        );
    }

    @Test
    void shouldLoginActiveSysAndIssueSession() {
        SysUserBO user = user(SysUserStatus.ACTIVE, true);
        SysLoginVO.SysProfileVO profile = new SysLoginVO.SysProfileVO(1L, "admin", "系统管理员", null, 1L);
        when(sysUserRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.passwordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("admin-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);
        when(sysUserConverter.toProfileVO(user)).thenReturn(profile);

        SysLoginVO result = sysLoginService.login(
                new SysLoginRequest(" Admin ", "correct-password")
        );

        assertEquals("admin-token", result.tokenValue());
        assertEquals(1L, result.user().id());
        assertEquals("admin", result.user().username());
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
        when(passwordHasher.matches("wrong-password", user.passwordHash())).thenReturn(false);

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
        when(passwordHasher.matches("correct-password", user.passwordHash())).thenReturn(true);

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
        when(passwordHasher.matches("correct-password", user.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> sysLoginService.login(new SysLoginRequest("admin", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    private static SysUserBO user(SysUserStatus status, boolean adminAccessEnabled) {
        return new SysUserBO(
                1L,
                "admin",
                "$2a$12$password-hash",
                "系统管理员",
                null,
                null,
                null,
                1L,
                status,
                false,
                adminAccessEnabled,
                null,
                null
        );
    }
}
