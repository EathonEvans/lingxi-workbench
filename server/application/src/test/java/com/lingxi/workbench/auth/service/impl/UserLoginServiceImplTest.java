package com.lingxi.workbench.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.common.enums.UserStatus;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.converter.UserConverter;
import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.auth.service.UserAvatarUrlService;
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
    private UserAvatarUrlService userAvatarUrlService;

    @Mock
    private LoginFailureGuard loginFailureGuard;

    @Mock
    private SaTokenInfo tokenInfo;

    private UserLoginServiceImpl userLoginService;

    @BeforeEach
    void setUp() {
        userLoginService = new UserLoginServiceImpl(
                userRepo,
                userConverter,
                passwordHasher,
                stpLogicDefinition,
                userAvatarUrlService,
                loginFailureGuard
        );
    }

    @Test
    void shouldLoginActiveUserAndIssueSession() {
        UserBO user = user(UserStatus.ACTIVE, "avatars/1/avatar.png");
        UserLoginVO.UserProfileVO profile = UserLoginVO.UserProfileVO.builder()
                .id(1L).username("alice").displayName("Alice")
                .avatarUrl("https://storage.example/avatar.png").build();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);
        when(stpLogicDefinition.login(1L)).thenReturn(tokenInfo);
        when(tokenInfo.getTokenName()).thenReturn("Authorization");
        when(tokenInfo.getTokenValue()).thenReturn("user-token");
        when(tokenInfo.getTokenTimeout()).thenReturn(7200L);
        when(userAvatarUrlService.resolve("avatars/1/avatar.png"))
                .thenReturn("https://storage.example/avatar.png");
        when(userConverter.toProfileVO(user, "https://storage.example/avatar.png"))
                .thenReturn(profile);

        UserLoginVO result = userLoginService.login(
                new UserLoginRequest(" Alice ", "correct-password")
        );

        assertEquals("user-token", result.tokenValue());
        assertEquals(1L, result.user().getId());
        assertEquals("alice", result.user().getUsername());
        assertEquals("https://storage.example/avatar.png", result.user().getAvatarUrl());
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
        UserBO user = user(UserStatus.ACTIVE, null);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectDisabledUserAfterPasswordVerification() {
        UserBO user = user(UserStatus.DISABLED, null);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectUserWithoutApplicationAccess() {
        UserBO user = UserBO.builder()
                .id(1L).username("alice").passwordHash("$2a$12$password-hash")
                .displayName("Alice").status(UserStatus.ACTIVE)
                .applicationAccessEnabled(false).build();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", user.getPasswordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectLoginWhenLockedByFailureGuard() {
        when(loginFailureGuard.isLocked("user", "alice")).thenReturn(true);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginRequest("alice", "correct-password"))
        );

        assertEquals("登录失败次数过多，账号已被临时锁定，请稍后再试", exception.getMessage());
        verify(userRepo, never()).findByUsername(anyString());
    }

    private static UserBO user(UserStatus status, String avatarUrl) {
        return UserBO.builder()
                .id(1L)
                .username("alice")
                .passwordHash("$2a$12$password-hash")
                .displayName("Alice")
                .avatarUrl(avatarUrl)
                .status(status)
                .applicationAccessEnabled(true)
                .build();
    }
}
