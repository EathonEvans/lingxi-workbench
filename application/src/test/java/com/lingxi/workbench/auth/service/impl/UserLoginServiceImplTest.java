package com.lingxi.workbench.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.common.enums.UserAccountStatus;
import com.lingxi.workbench.auth.model.bo.UserAccountBO;
import com.lingxi.workbench.auth.model.converter.UserAccountConverter;
import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.repo.UserAccountRepo;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceImplTest {

    @Mock
    private UserAccountRepo userAccountRepo;

    @Mock
    private UserAccountConverter userAccountConverter;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private SaTokenInfo tokenInfo;

    private UserLoginServiceImpl userLoginService;

    @BeforeEach
    void setUp() {
        userLoginService = new UserLoginServiceImpl(
                userAccountRepo,
                userAccountConverter,
                passwordHasher,
                stpLogicDefinition
        );
    }

    @Test
    void shouldLoginActiveUserAndIssueSession() {
        UserAccountBO account = account(UserAccountStatus.ACTIVE);
        UserLoginVO.UserProfileVO profile = new UserLoginVO.UserProfileVO(1L, "alice", "Alice", null);
        when(userAccountRepo.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("user-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);
        when(userAccountConverter.toProfileVO(account)).thenReturn(profile);

        UserLoginVO result = userLoginService.login(
                new UserLoginRequest(" Alice ", "correct-password")
        );

        assertEquals("user-token", result.tokenValue());
        assertEquals(1L, result.user().id());
        assertEquals("alice", result.user().username());
    }


    @Test
    void shouldHideWhetherUsernameExists() {
        when(userAccountRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("missing", "password"))
        );

        verify(passwordHasher).matches(org.mockito.ArgumentMatchers.eq("password"), anyString());
        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectInvalidPassword() {
        UserAccountBO account = account(UserAccountStatus.ACTIVE);
        when(userAccountRepo.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong-password", account.passwordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectDisabledAccountAfterPasswordVerification() {
        UserAccountBO account = account(UserAccountStatus.DISABLED);
        when(userAccountRepo.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    private static UserAccountBO account(UserAccountStatus status) {
        return new UserAccountBO(
                1L,
                "alice",
                "$2a$12$password-hash",
                "Alice",
                null,
                status
        );
    }
}
