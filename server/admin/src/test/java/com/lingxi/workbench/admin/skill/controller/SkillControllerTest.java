package com.lingxi.workbench.admin.skill.controller;

import com.lingxi.workbench.admin.config.SysSaTokenAuthWebConfig;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.admin.skill.model.request.SkillPageQuery;
import com.lingxi.workbench.admin.skill.service.SkillService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = SkillController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SysSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillService skillService;

    @MockitoBean
    private SysAuthenticationInterceptor sysAuthenticationInterceptor;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(7L, "root", "超级管理员", null));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnSkillPageForCurrentOperator() throws Exception {
        when(skillService.page(any(SkillPageQuery.class), eq(7L)))
                .thenReturn(new PageResult<>(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/skills/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(skillService).page(any(SkillPageQuery.class), eq(7L));
    }

    @Test
    void shouldRejectInvalidDraftBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/skills/save-draft/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instructions":"",
                                  "activationMode":"EXPLICIT_ONLY",
                                  "requestedToolCodes":[],
                                  "expectedRevision":0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.BAD_REQUEST.getCode()));

        verifyNoInteractions(skillService);
    }

    @Test
    void shouldPublishExpectedDraftRevisionForCurrentOperator() throws Exception {
        mockMvc.perform(post("/api/v1/skills/publish/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedRevision":3,
                                  "versionName":"首个正式版本"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()));

        verify(skillService).publish(
                eq(10L),
                argThat(request -> request.getExpectedRevision() == 3L
                        && "首个正式版本".equals(request.getVersionName())),
                eq(7L));
    }

    @Test
    void shouldActivateRequestedVersionForCurrentOperator() throws Exception {
        mockMvc.perform(post("/api/v1/skills/activate-version/10/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()));

        verify(skillService).activateVersion(10L, 101L, 7L);
    }

    @Test
    void shouldRevokeRequestedVersionWithReasonForCurrentOperator() throws Exception {
        mockMvc.perform(post("/api/v1/skills/revoke-version/10/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason":"安全策略变化"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()));

        verify(skillService).revokeVersion(
                eq(10L), eq(101L), argThat(request -> "安全策略变化".equals(request.reason())), eq(7L));
    }
}
