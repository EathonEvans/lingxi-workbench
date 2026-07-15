package com.lingxi.workbench.auth.interceptor;

import com.lingxi.workbench.auth.common.enums.UserAccountStatus;
import com.lingxi.workbench.auth.config.interceptor.UserAuthenticationInterceptor;
import com.lingxi.workbench.auth.context.UserContext;
import com.lingxi.workbench.auth.model.bo.UserAccountBO;
import com.lingxi.workbench.auth.repo.UserAccountRepo;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.context.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationInterceptorTest {

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private UserAccountRepo userAccountRepo;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldInitializeAndClearUserContext() throws Exception {
        when(stpLogicDefinition.getLoginIdAsLong()).thenReturn(1L);
        when(userAccountRepo.findById(1L)).thenReturn(Optional.of(account()));
        UserAuthenticationInterceptor interceptor = new UserAuthenticationInterceptor(
                stpLogicDefinition,
                userAccountRepo
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();

        assertTrue(interceptor.preHandle(request, response, handler));
        LoginUser loginUser = UserContext.get();
        assertEquals(1L, loginUser.id());
        assertEquals("alice", loginUser.username());

        interceptor.afterCompletion(request, response, handler, null);
        assertThrows(IllegalStateException.class, UserContext::get);
    }

    private static UserAccountBO account() {
        return new UserAccountBO(
                1L,
                "alice",
                "password-hash",
                "Alice",
                null,
                UserAccountStatus.ACTIVE
        );
    }
}
