package com.lingxi.workbench.admin.auth.infrastructure.web;

import com.lingxi.workbench.admin.auth.AdminUserContext;
import com.lingxi.workbench.admin.auth.domain.AdminAccount;
import com.lingxi.workbench.admin.auth.domain.AdminAccountRepository;
import com.lingxi.workbench.admin.auth.domain.AdminAccountStatus;
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
class AdminAuthenticationInterceptorTest {

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private AdminAccountRepository adminAccountRepository;

    @AfterEach
    void clearContext() {
        AdminUserContext.clear();
    }

    @Test
    void shouldInitializeAndClearAdminContext() throws Exception {
        when(stpLogicDefinition.getLoginIdAsLong()).thenReturn(1L);
        when(adminAccountRepository.findById(1L)).thenReturn(Optional.of(account()));
        AdminAuthenticationInterceptor interceptor = new AdminAuthenticationInterceptor(
                stpLogicDefinition,
                adminAccountRepository
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();

        assertTrue(interceptor.preHandle(request, response, handler));
        LoginUser loginUser = AdminUserContext.get();
        assertEquals(1L, loginUser.id());
        assertEquals("admin", loginUser.username());

        interceptor.afterCompletion(request, response, handler, null);
        assertThrows(IllegalStateException.class, AdminUserContext::get);
    }

    private static AdminAccount account() {
        return new AdminAccount(
                1L,
                "admin",
                "password-hash",
                "系统管理员",
                null,
                AdminAccountStatus.ACTIVE
        );
    }
}
