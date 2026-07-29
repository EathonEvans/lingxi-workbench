package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Application 运行时业务测试。
 */
class ApplicationRuntimeServiceImplTest {

    @Test
    void shouldExposeStableCodeInsteadOfPublishedVersionId() {
        RuntimeApplicationEntity application = new RuntimeApplicationEntity();
        application.setId(21L);
        application.setName("灵析通用助手");
        application.setCode("general_assistant");
        application.setDescription("企业知识问答");
        application.setPublishedVersionId(35L);

        ApplicationRuntimeServiceImpl service =
                new ApplicationRuntimeServiceImpl(new StubApplicationRuntimeRepo(application));

        List<AvailableApplicationVO> applications = service.listAvailableApplications();

        assertEquals(
                List.of(new AvailableApplicationVO(
                        "general_assistant", "灵析通用助手", "企业知识问答")),
                applications);
    }

    /**
     * 只替换本测试关心的只读仓储边界，避免测试依赖数据库或 Mock 框架代理能力。
     */
    private static final class StubApplicationRuntimeRepo extends ApplicationRuntimeRepo {

        private final RuntimeApplicationEntity application;

        private StubApplicationRuntimeRepo(RuntimeApplicationEntity application) {
            super(null, null);
            this.application = application;
        }

        @Override
        public List<RuntimeApplicationEntity> findAvailableApplications() {
            return List.of(application);
        }
    }
}
