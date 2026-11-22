package com.lingxi.workbench.application.runtime.controller;

import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.model.vo.ApplicationAssistantIdentityVO;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 应用运行端接口契约测试。
 */
class ApplicationRuntimeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApplicationVersionService service = mock(ApplicationVersionService.class);
        when(service.listAvailableApplications()).thenReturn(List.of(
                new AvailableApplicationVO(
                        "general_assistant", "灵析通用助手", "企业知识问答",
                        new ApplicationAssistantIdentityVO("灵析通用助手", "https://example.com/avatar.png"))));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationRuntimeController(service))
                .build();
    }

    @Test
    void shouldReturnAvailableApplicationsWithStableCode() throws Exception {
        mockMvc.perform(get("/api/v1/applications/available"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "code": 200,
                          "data": [{
                            "code": "general_assistant",
                            "name": "灵析通用助手",
                            "description": "企业知识问答",
                            "assistant": {
                              "name": "灵析通用助手",
                              "avatar": "https://example.com/avatar.png"
                            }
                          }],
                          "msg": "成功"
                        }
                        """));
    }

}
