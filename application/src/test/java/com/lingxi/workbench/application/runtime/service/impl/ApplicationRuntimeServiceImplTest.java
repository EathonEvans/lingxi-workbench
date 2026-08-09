package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

/**
 * Application 运行时业务测试。
 */
class ApplicationRuntimeServiceImplTest {

    @Test
    void shouldExposeStableCodeInsteadOfPublishedVersionId() {
        RuntimeApplicationEntity application = application();

        ApplicationRuntimeServiceImpl service =
                new ApplicationRuntimeServiceImpl(new StubApplicationRuntimeRepo(application, null));

        List<AvailableApplicationVO> applications = service.listAvailableApplications();

        assertEquals(
                List.of(new AvailableApplicationVO(
                        "general_assistant", "灵析通用助手", "企业知识问答")),
                applications);
    }

    @Test
    void shouldResolvePublishedApplicationWithOnlyDefaultAgent() {
        RuntimeApplicationEntity application = application();
        RuntimeApplicationVersionEntity version = version(executableDefinition());
        ApplicationRuntimeServiceImpl service = new ApplicationRuntimeServiceImpl(
                new StubApplicationRuntimeRepo(application, version));

        AppRuntimeSnapshot snapshot = assertDoesNotThrow(
                () -> service.loadRuntimeAppConfig("GENERAL_ASSISTANT"));

        assertEquals(35L, snapshot.versionId());
        assertEquals(40L, snapshot.definition().defaultAgent().agentId());
    }

    @Test
    void shouldRejectPublishedApplicationWithoutExecutionEntry() {
        RuntimeApplicationEntity application = application();
        RuntimeApplicationVersionEntity version = version(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .build());
        ApplicationRuntimeServiceImpl service = new ApplicationRuntimeServiceImpl(
                new StubApplicationRuntimeRepo(application, version));

        assertThrows(BusinessException.class, () -> service.loadRuntimeAppConfig("general_assistant"));
    }

    private static RuntimeApplicationEntity application() {
        RuntimeApplicationEntity application = new RuntimeApplicationEntity();
        application.setId(21L);
        application.setName("灵析通用助手");
        application.setCode("general_assistant");
        application.setDescription("企业知识问答");
        application.setPublishedVersionId(35L);
        return application;
    }

    private static RuntimeApplicationVersionEntity version(ApplicationDefinition definition) {
        RuntimeApplicationVersionEntity version = new RuntimeApplicationVersionEntity();
        version.setId(35L);
        version.setApplicationId(21L);
        version.setDefinitionSnapshot(definition);
        return version;
    }

    private static ApplicationDefinition executableDefinition() {
        ApplicationAgentReference defaultAgent = new ApplicationAgentReference(40L, 41L, "默认助手", "处理通用任务");
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(40L)
                .targetVersionId(41L)
                .build();
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(defaultAgent)
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                .build();
    }

    /**
     * 只替换本测试关心的只读仓储边界，避免测试依赖数据库或 Mock 框架代理能力。
     */
    private static final class StubApplicationRuntimeRepo extends ApplicationRuntimeRepo {

        private final RuntimeApplicationEntity application;
        private final RuntimeApplicationVersionEntity version;

        private StubApplicationRuntimeRepo(RuntimeApplicationEntity application, RuntimeApplicationVersionEntity version) {
            super(null, null);
            this.application = application;
            this.version = version;
        }

        @Override
        public List<RuntimeApplicationEntity> findAvailableApplications() {
            return List.of(application);
        }

        @Override
        public Optional<RuntimeApplicationEntity> findActiveByCode(String code) {
            return Optional.of(application);
        }

        @Override
        public Optional<RuntimeApplicationVersionEntity> findVersion(Long applicationId, Long versionId) {
            return Optional.ofNullable(version);
        }
    }
}
