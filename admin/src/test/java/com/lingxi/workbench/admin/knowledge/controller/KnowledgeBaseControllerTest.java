package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.config.SysSaTokenAuthWebConfig;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessVO;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeBaseService;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = KnowledgeBaseController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = SysSaTokenAuthWebConfig.class))
@Import(GlobalExceptionHandler.class)
class KnowledgeBaseControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private KnowledgeBaseService service;
    @MockitoBean private SysAuthenticationInterceptor interceptor;

    @Test
    void shouldReturnExplicitAccessContract() throws Exception {
        when(service.access(1L)).thenReturn(new KnowledgeAccessVO(KnowledgeAccessMode.RESTRICTED, Set.of(12L), Set.of(), Set.of()));
        mvc.perform(get("/api/v1/knowledge-bases/access/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.userIds[0]").value(12));
    }

    @Test
    void shouldRejectMissingSubjectCollectionsAtTransportBoundary() throws Exception {
        mvc.perform(post("/api/v1/knowledge-bases/access/update/1").contentType("application/json")
                .content("{\"mode\":\"RESTRICTED\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectInvalidSubjectIdentifier() throws Exception {
        mvc.perform(post("/api/v1/knowledge-bases/access/update/1").contentType("application/json")
                .content("{\"mode\":\"RESTRICTED\",\"userIds\":[-1],\"roleIds\":[],\"departmentIds\":[]}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void allAccessEndpointsMustRequireKnowledgeManagementPermission() {
        Arrays.stream(KnowledgeBaseController.class.getDeclaredMethods())
                .filter(method -> Set.of("access", "saveAccess", "userOptions", "roleOptions", "departmentOptions").contains(method.getName()))
                .forEach(method -> assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly("knowledge:manage"));
    }
}
