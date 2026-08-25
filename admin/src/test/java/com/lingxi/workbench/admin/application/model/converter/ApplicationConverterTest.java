package com.lingxi.workbench.admin.application.model.converter;

import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVO;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

/** Application 管理端响应转换回归测试。 */
class ApplicationConverterTest {

    @Test
    void shouldMarkOnlyCurrentSchemaVersionAsReady() {
        AiApplicationEntity application = new AiApplicationEntity();
        application.setId(1L);
        application.setPublishedVersionId(11L);
        AiApplicationVersionEntity version = new AiApplicationVersionEntity();
        version.setId(11L);
        version.setVersionNo(3);
        version.setSchemaVersion(APPLICATION_SCHEMA_VERSION - 1);
        version.setDefinitionSnapshot(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION - 1)
                .build());
        ApplicationConverter converter = new ApplicationConverter();

        ApplicationVO legacy = converter.toListVO(application, version, null);
        version.setSchemaVersion(APPLICATION_SCHEMA_VERSION);
        version.setDefinitionSnapshot(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .build());
        ApplicationVO incomplete = converter.toListVO(application, version, null);
        version.setDefinitionSnapshot(publishedDefinition());
        ApplicationVO current = converter.toListVO(application, version, null);

        assertThat(legacy.isPublishedVersionReady()).isFalse();
        assertThat(incomplete.isPublishedVersionReady()).isFalse();
        assertThat(current.isPublishedVersionReady()).isTrue();
    }

    private static ApplicationDefinition publishedDefinition() {
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(10L, 11L, "主Agent", "负责收口"),
                        List.of(), List.of(), List.of()))
                .memoryConf(MemoryConfig.disabled())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.empty())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();
    }
}
