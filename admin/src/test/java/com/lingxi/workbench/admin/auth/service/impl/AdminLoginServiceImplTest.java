package com.lingxi.workbench.admin.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.auth.common.enums.AdminAccountStatus;
import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.model.converter.AdminAccountConverter;
import com.lingxi.workbench.admin.auth.model.request.AdminLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminLoginVO;
import com.lingxi.workbench.admin.auth.repo.AdminAccountRepo;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
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
class AdminLoginServiceImplTest {

    @Mock
    private AdminAccountRepo adminAccountRepo;

    @Mock
    private AdminAccountConverter adminAccountConverter;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private SaTokenInfo tokenInfo;

    private AdminLoginServiceImpl adminLoginService;

    @BeforeEach
    void setUp() {
        adminLoginService = new AdminLoginServiceImpl(
                adminAccountRepo,
                adminAccountConverter,
                passwordHasher,
                stpLogicDefinition
        );
    }

    @Test
    void shouldLoginActiveAdminAndIssueSession() {
        AdminAccountBO account = account(AdminAccountStatus.ACTIVE);
        AdminLoginVO.AdminProfileVO profile = new AdminLoginVO.AdminProfileVO(1L, "admin", "系统管理员", null, 1L);
        when(adminAccountRepo.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("admin-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);
        when(adminAccountConverter.toProfileVO(account)).thenReturn(profile);

        AdminLoginVO result = adminLoginService.login(
                new AdminLoginRequest(" Admin ", "correct-password")
        );

        assertEquals("admin-token", result.tokenValue());
        assertEquals(1L, result.admin().id());
        assertEquals("admin", result.admin().username());
    }

    @Test
    void shouldHideWhetherAdminExists() {
        when(adminAccountRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> adminLoginService.login(new AdminLoginRequest("missing", "password"))
        );

        verify(passwordHasher).matches(eq("password"), anyString());
        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectInvalidPassword() {
        AdminAccountBO account = account(AdminAccountStatus.ACTIVE);
        when(adminAccountRepo.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong-password", account.passwordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> adminLoginService.login(new AdminLoginRequest("admin", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectDisabledAdminAfterPasswordVerification() {
        AdminAccountBO account = account(AdminAccountStatus.DISABLED);
        when(adminAccountRepo.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> adminLoginService.login(new AdminLoginRequest("admin", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    private static AdminAccountBO account(AdminAccountStatus status) {
        return new AdminAccountBO(
                1L,
                "admin",
                "$2a$12$password-hash",
                "系统管理员",
                null,
                1L,
                status
        );
    }
}
