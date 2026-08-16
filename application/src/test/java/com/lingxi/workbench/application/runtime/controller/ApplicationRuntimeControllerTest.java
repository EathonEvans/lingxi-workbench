package com.lingxi.workbench.application.runtime.controller;

import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.app.ApplicationRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 应用运行端接口契约测试。
 */
class ApplicationRuntimeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApplicationRuntimeService service = new StubApplicationRuntimeService();
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
                            "description": "企业知识问答"
                          }],
                          "msg": "成功"
                        }
                        """));
    }

    /**
     * Controller 契约测试只提供固定业务结果，不引入数据库和 Mock 代理。
     */
    private static final class StubApplicationRuntimeService
            implements ApplicationRuntimeService {

        @Override
        public List<AvailableApplicationVO> listAvailableApplications() {
            return List.of(
                    new AvailableApplicationVO(
                            "general_assistant", "灵析通用助手", "企业知识问答")
            );
        }

        @Override
        public AppRuntimeSnapshot loadRuntimeAppConfig(String applicationCode) {
            throw new UnsupportedOperationException("当前测试不覆盖应用版本解析");
        }

        @Override
        public AppRuntimeSnapshot loadRuntimeAppVersion(Long applicationId, Long applicationVersionId) {
            throw new UnsupportedOperationException("当前测试不覆盖冻结应用版本解析");
        }
    }
}
