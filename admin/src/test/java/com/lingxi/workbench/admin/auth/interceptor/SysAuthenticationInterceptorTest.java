package com.lingxi.workbench.admin.auth.interceptor;

import com.lingxi.workbench.admin.auth.common.enums.SysUserStatus;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.admin.auth.context.SysUserContext;
import com.lingxi.workbench.admin.auth.model.bo.SysUserBO;
import com.lingxi.workbench.admin.auth.repo.SysUserRepo;
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
class SysAuthenticationInterceptorTest {

    @Mock
    private StpLogicDefinition stpLogicDefinition;

    @Mock
    private SysUserRepo sysUserRepo;

    @AfterEach
    void clearContext() {
        SysUserContext.clear();
    }

    @Test
    void shouldInitializeAndClearSysContext() throws Exception {
        when(stpLogicDefinition.getLoginIdAsLong()).thenReturn(1L);
        when(sysUserRepo.findById(1L)).thenReturn(Optional.of(user()));
        SysAuthenticationInterceptor interceptor = new SysAuthenticationInterceptor(
                stpLogicDefinition,
                sysUserRepo
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();

        assertTrue(interceptor.preHandle(request, response, handler));
        LoginUser loginUser = SysUserContext.get();
        assertEquals(1L, loginUser.id());
        assertEquals("admin", loginUser.username());

        interceptor.afterCompletion(request, response, handler, null);
        assertThrows(IllegalStateException.class, SysUserContext::get);
    }

    private static SysUserBO user() {
        return new SysUserBO(
                1L,
                "admin",
                "password-hash",
                "系统管理员",
                null,
                1L,
                SysUserStatus.ACTIVE
        );
    }
}
