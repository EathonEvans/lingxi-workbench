package com.lingxi.workbench.admin.application.controller;

import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.admin.application.service.ApplicationIntentPreviewService;
import com.lingxi.workbench.admin.config.SysSaTokenAuthWebConfig;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = ApplicationIntentPreviewController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SysSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class ApplicationIntentPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationIntentPreviewService previewService;

    @MockitoBean
    private SysAuthenticationInterceptor sysAuthenticationInterceptor;

    @Test
    void shouldReturnIntentPreviewEvidence() throws Exception {
        ApplicationIntentPreviewVO result = new ApplicationIntentPreviewVO();
        result.setSemanticAction("SELECT");
        result.setFinalAction("EXECUTE_WORKFLOW");
        when(previewService.preview(eq(10L), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/applications/preview-intent/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"我要退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.semanticAction").value("SELECT"))
                .andExpect(jsonPath("$.data.finalAction").value("EXECUTE_WORKFLOW"));
    }

    @Test
    void shouldRejectBlankQueryBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/applications/preview-intent/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(previewService);
    }
}
