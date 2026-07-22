package com.lingxi.workbench.admin.auth.interceptor;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.AuthException;
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
class SysAuthenticationInterceptorTest {

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private SysUserRepo sysUserRepo;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldInitializeAndClearSysContext() throws Exception {
        when(stpLogicDefinition.getLoginIdAsLong()).thenReturn(1L);
        when(sysUserRepo.findById(1L)).thenReturn(Optional.of(user(true)));
        SysAuthenticationInterceptor interceptor = new SysAuthenticationInterceptor(
                stpLogicDefinition,
                sysUserRepo
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();

        assertTrue(interceptor.preHandle(request, response, handler));
        LoginUser loginUser = UserContext.get();
        assertEquals(1L, loginUser.id());
        assertEquals("admin", loginUser.username());

        interceptor.afterCompletion(request, response, handler, null);
        assertThrows(IllegalStateException.class, UserContext::get);
    }

    @Test
    void shouldRejectUserWithoutAdminAccess() {
        when(stpLogicDefinition.getLoginIdAsLong()).thenReturn(1L);
        when(sysUserRepo.findById(1L)).thenReturn(Optional.of(user(false)));
        SysAuthenticationInterceptor interceptor = new SysAuthenticationInterceptor(
                stpLogicDefinition,
                sysUserRepo
        );

        assertThrows(
                AuthException.class,
                () -> interceptor.preHandle(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        new Object()
                )
        );
    }

    private static SysUserBO user(boolean adminAccessEnabled) {
        return new SysUserBO(
                1L,
                "admin",
                "password-hash",
                "系统管理员",
                null,
                null,
                null,
                1L,
                SysUserStatus.ACTIVE,
                false,
                adminAccessEnabled,
                null,
                null
        );
    }
}
