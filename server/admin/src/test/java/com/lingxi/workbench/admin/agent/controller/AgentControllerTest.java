package com.lingxi.workbench.admin.agent.controller;

import com.lingxi.workbench.admin.agent.model.vo.AgentSkillOptionVO;
import com.lingxi.workbench.admin.agent.service.AgentService;
import com.lingxi.workbench.admin.config.SysSaTokenAuthWebConfig;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AgentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SysSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentService agentService;

    @MockitoBean
    private SysAuthenticationInterceptor sysAuthenticationInterceptor;

    @Test
    void shouldReturnAgentSkillOptions() throws Exception {
        AgentSkillOptionVO option = AgentSkillOptionVO.builder()
                .id("1")
                .versionId("11")
                .versionNo(2)
                .code("fact_check")
                .name("事实核验")
                .description("核验事实来源")
                .activationMode(SkillActivationMode.ALWAYS_ON)
                .requestedToolCodes(List.of("knowledge_search"))
                .build();
        when(agentService.skillOptions()).thenReturn(List.of(option));

        mockMvc.perform(get("/api/v1/agents/skill-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()))
                .andExpect(jsonPath("$.data[0].name").value("事实核验"))
                .andExpect(jsonPath("$.data[0].activationMode").value("ALWAYS_ON"));
    }
}
