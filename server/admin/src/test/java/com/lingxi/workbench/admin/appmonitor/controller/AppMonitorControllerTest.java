package com.lingxi.workbench.admin.appmonitor.controller;

import com.lingxi.workbench.admin.appmonitor.model.vo.AppOptionVO;
import com.lingxi.workbench.admin.appmonitor.service.AppMonitorService;
import com.lingxi.workbench.admin.config.SysSaTokenAuthWebConfig;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AppMonitorController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SysSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class AppMonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppMonitorService monitorService;

    @MockitoBean
    private SysAuthenticationInterceptor sysAuthenticationInterceptor;

    @Test
    void shouldReturnApplicationOptions() throws Exception {
        when(monitorService.options()).thenReturn(List.of(new AppOptionVO("1", "求职助手", "career_assistant")));

        mockMvc.perform(get("/api/v1/app-monitor/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].name").value("求职助手"));
    }

    @Test
    void shouldRejectRangeBeyondSevenDaysBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/app-monitor/overview").queryParam("rangeHours", "169"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.BAD_REQUEST.getCode()));

        verifyNoInteractions(monitorService);
    }

    @Test
    void shouldRejectMalformedTraceIdBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/app-monitor/traces/not-a-trace"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.BAD_REQUEST.getCode()));

        verifyNoInteractions(monitorService);
    }
}
