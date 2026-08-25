package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.MemoryConfig;
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
        RuntimeApplicationVersionEntity version = version(executableDefinition());

        ApplicationRuntimeServiceImpl service =
                new ApplicationRuntimeServiceImpl(new StubApplicationRuntimeRepo(application, version));

        List<AvailableApplicationVO> applications = service.listAvailableApplications();

        assertEquals(
                List.of(new AvailableApplicationVO(
                        "general_assistant", "灵析通用助手", "企业知识问答")),
                applications);
    }

    @Test
    void shouldHideApplicationWithUnsupportedPublishedSchema() {
        RuntimeApplicationEntity application = application();
        RuntimeApplicationVersionEntity version = version(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION - 1)
                .build());
        ApplicationRuntimeServiceImpl service = new ApplicationRuntimeServiceImpl(
                new StubApplicationRuntimeRepo(application, version));

        List<AvailableApplicationVO> applications = service.listAvailableApplications();

        assertEquals(List.of(), applications);
    }

    @Test
    void shouldHideApplicationWhenPublishedVersionBelongsToAnotherApplication() {
        RuntimeApplicationEntity application = application();
        RuntimeApplicationVersionEntity version = version(executableDefinition());
        version.setApplicationId(99L);
        ApplicationRuntimeServiceImpl service = new ApplicationRuntimeServiceImpl(
                new StubApplicationRuntimeRepo(application, version));

        List<AvailableApplicationVO> applications = service.listAvailableApplications();

        assertEquals(List.of(), applications);
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
        assertEquals("default_agent", snapshot.definition().fallback().cap());
    }

    @Test
    void shouldRejectPublishedApplicationWithoutFallbackAgent() {
        RuntimeApplicationEntity application = application();
        ApplicationDefinition definition = executableDefinition();
        definition.setFallback(null);
        RuntimeApplicationVersionEntity version = version(definition);
        ApplicationRuntimeServiceImpl service = new ApplicationRuntimeServiceImpl(
                new StubApplicationRuntimeRepo(application, version));

        assertThrows(BusinessException.class, () -> service.loadRuntimeAppConfig("general_assistant"));
    }

    @Test
    void shouldRejectIncompletePublishedDefinitionWithoutRuntimeDefaulting() {
        RuntimeApplicationEntity application = application();
        ApplicationDefinition definition = executableDefinition();
        definition.setMemoryConf(null);
        RuntimeApplicationVersionEntity version = version(definition);
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
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(40L)
                .targetVersionId(41L)
                .build();
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(40L, 41L, "通用助手", "负责默认收口"),
                        List.of(), List.of(), List.of()))
                .memoryConf(MemoryConfig.disabled())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
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
        public List<RuntimeApplicationVersionEntity> findVersionsByIds(List<Long> versionIds) {
            return Optional.ofNullable(version).stream().toList();
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
