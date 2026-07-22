package com.lingxi.workbench.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.common.enums.UserStatus;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.converter.UserConverter;
import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.repo.UserRepo;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceImplTest {

    @Mock
    private UserRepo userRepo;

    @Mock
    private UserConverter userConverter;

    @Mock
    private BCryptPasswordHasher passwordHasher;

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private SaTokenInfo tokenInfo;

    private UserLoginServiceImpl userLoginService;

    @BeforeEach
    void setUp() {
        userLoginService = new UserLoginServiceImpl(
                userRepo,
                userConverter,
                passwordHasher,
                stpLogicDefinition
        );
    }

    @Test
    void shouldLoginActiveUserAndIssueSession() {
        UserBO user = user(UserStatus.ACTIVE);
        UserLoginVO.UserProfileVO profile = new UserLoginVO.UserProfileVO(1L, "alice", "Alice", null);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.passwordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("user-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);
        when(userConverter.toProfileVO(user)).thenReturn(profile);

        UserLoginVO result = userLoginService.login(
                new UserLoginRequest(" Alice ", "correct-password")
        );

        assertEquals("user-token", result.tokenValue());
        assertEquals(1L, result.user().id());
        assertEquals("alice", result.user().username());
    }


    @Test
    void shouldHideWhetherUsernameExists() {
        when(userRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("missing", "password"))
        );

        verify(passwordHasher).timingSafeDummyMatch(org.mockito.ArgumentMatchers.eq("password"));
        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectInvalidPassword() {
        UserBO user = user(UserStatus.ACTIVE);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong-password", user.passwordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectDisabledUserAfterPasswordVerification() {
        UserBO user = user(UserStatus.DISABLED);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectUserWithoutApplicationAccess() {
        UserBO user = new UserBO(
                1L, "alice", "$2a$12$password-hash", "Alice", null,
                null, null, UserStatus.ACTIVE, false
        );
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    private static UserBO user(UserStatus status) {
        return new UserBO(
                1L,
                "alice",
                "$2a$12$password-hash",
                "Alice",
                null,
                status
        );
    }
}
