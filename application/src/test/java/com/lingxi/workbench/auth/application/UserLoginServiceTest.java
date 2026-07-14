package com.lingxi.workbench.auth.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.domain.UserAccount;
import com.lingxi.workbench.auth.domain.UserAccountRepository;
import com.lingxi.workbench.auth.domain.UserAccountStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    private UserLoginService userLoginService;

    @BeforeEach
    void setUp() {
        userLoginService = new UserLoginService(
                userAccountRepository,
                passwordHasher,
                stpLogicDefinition
        );
    }


    @Test
    void shouldHideWhetherUsernameExists() {
        when(userAccountRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginCommand("missing", "password"))
        );

        verify(passwordHasher).matches(org.mockito.ArgumentMatchers.eq("password"), anyString());
        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectInvalidPassword() {
        UserAccount account = account(UserAccountStatus.ACTIVE);
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong-password", account.passwordHash())).thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginCommand("alice", "wrong-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectDisabledAccountAfterPasswordVerification() {
        UserAccount account = account(UserAccountStatus.DISABLED);
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("correct-password", account.passwordHash())).thenReturn(true);

        assertThrows(
                AuthException.class,
                () -> userLoginService.login(new UserLoginCommand("alice", "correct-password"))
        );

        verify(stpLogicDefinition, never()).login(org.mockito.ArgumentMatchers.anyLong());
    }

    private static UserAccount account(UserAccountStatus status) {
        return new UserAccount(
                1L,
                "alice",
                "$2a$12$password-hash",
                "Alice",
                null,
                status
        );
    }
}
