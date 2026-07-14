package com.lingxi.workbench.auth.api;

import com.lingxi.workbench.auth.application.UserLoginCommand;
import com.lingxi.workbench.auth.application.UserLoginResult;
import com.lingxi.workbench.auth.application.UserLoginService;
import com.lingxi.workbench.auth.infrastructure.web.UserAuthenticationInterceptor;
import com.lingxi.workbench.auth.infrastructure.web.UserSaTokenAuthWebConfig;
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
        value = UserLoginController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = UserSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class UserLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserLoginService userLoginService;

    @MockitoBean
    private UserAuthenticationInterceptor userAuthenticationInterceptor;

    @Test
    void shouldReturnLoginTokenAndUserProfile() throws Exception {
        when(userLoginService.login(any(UserLoginCommand.class))).thenReturn(
                new UserLoginResult(
                        "Authorization",
                        "token-value",
                        7200,
                        1L,
                        "alice",
                        "Alice",
                        null
                )
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("成功"))
                .andExpect(jsonPath("$.data.tokenName").value("Authorization"))
                .andExpect(jsonPath("$.data.tokenValue").value("token-value"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(7200))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.username").value("alice"));
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

        verifyNoInteractions(userLoginService);
    }

    @Test
    void shouldReturnGenericUnauthorizedError() throws Exception {
        when(userLoginService.login(any(UserLoginCommand.class)))
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
