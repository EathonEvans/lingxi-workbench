package com.lingxi.workbench.admin.auth.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.auth.domain.AdminAccount;
import com.lingxi.workbench.admin.auth.domain.AdminAccountRepository;
import com.lingxi.workbench.admin.auth.domain.AdminAccountStatus;
import com.lingxi.workbench.framework.auth.AuthException;
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
class AdminLoginServiceTest {

    @Mock
    private AdminAccountRepository adminAccountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private SaTokenInfo tokenInfo;

    private AdminLoginService adminLoginService;

    @BeforeEach
    void setUp() {
        adminLoginService = new AdminLoginService(
                adminAccountRepository,
                passwordHasher,
                stpLogicDefinition
        );
    }

    @Test
    void shouldLoginActiveAdminAndIssueSession() {
        AdminAccount account = account(AdminAccountStatus.ACTIVE);
        when(adminAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("admin-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);

        AdminLoginResult result = adminLoginService.login(
                new AdminLoginCommand(" Admin ", "correct-password")
        );

        assertEquals("admin-token", result.tokenValue());
        assertEquals(1L, result.adminId());
        assertEquals("admin", result.username());
    }

    @Test
    void shouldHideWhetherAdminExists() {
        when(adminAccountRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> adminLoginService.login(new AdminLoginCommand("missing", "password"))
        );

        verify(passwordHasher).matches(eq("password"), anyString());
        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectInvalidPassword() {
        AdminAccount account = account(AdminAccountStatus.ACTIVE);
        when(adminAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong-password", account.passwordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> adminLoginService.login(new AdminLoginCommand("admin", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    @Test
    void shouldRejectDisabledAdminAfterPasswordVerification() {
        AdminAccount account = account(AdminAccountStatus.DISABLED);
        when(adminAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> adminLoginService.login(new AdminLoginCommand("admin", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(anyLong());
    }

    private static AdminAccount account(AdminAccountStatus status) {
        return new AdminAccount(
                1L,
                "admin",
                "$2a$12$password-hash",
                "系统管理员",
                null,
                status
        );
    }
}
