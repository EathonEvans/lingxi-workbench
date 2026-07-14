package com.lingxi.workbench.admin.auth.api;

import com.lingxi.workbench.admin.auth.application.AdminLoginCommand;
import com.lingxi.workbench.admin.auth.application.AdminLoginResult;
import com.lingxi.workbench.admin.auth.application.AdminLoginService;
import com.lingxi.workbench.admin.auth.infrastructure.web.AdminAuthenticationInterceptor;
import com.lingxi.workbench.admin.auth.infrastructure.web.AdminSaTokenAuthWebConfig;
import com.lingxi.workbench.framework.auth.AuthException;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AdminLoginController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AdminSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class AdminLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminLoginService adminLoginService;

    @MockitoBean
    private AdminAuthenticationInterceptor adminAuthenticationInterceptor;

    @Test
    void shouldReturnAdminTokenAndProfile() throws Exception {
        when(adminLoginService.login(any(AdminLoginCommand.class))).thenReturn(
                new AdminLoginResult(
                        "Authorization",
                        "admin-token",
                        7200,
                        1L,
                        "admin",
                        "系统管理员",
                        null
                )
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("成功"))
                .andExpect(jsonPath("$.data.tokenName").value("Authorization"))
                .andExpect(jsonPath("$.data.tokenValue").value("admin-token"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(7200))
                .andExpect(jsonPath("$.data.admin.id").value(1))
                .andExpect(jsonPath("$.data.admin.username").value("admin"));
    }

    @Test
    void shouldRejectInvalidRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数不合法"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(adminLoginService);
    }

    @Test
    void shouldReturnGenericUnauthorizedError() throws Exception {
        when(adminLoginService.login(any(AdminLoginCommand.class)))
                .thenThrow(AuthException.invalidCredentials());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
