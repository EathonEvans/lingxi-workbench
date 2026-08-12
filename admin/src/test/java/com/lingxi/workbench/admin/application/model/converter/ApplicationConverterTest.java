package com.lingxi.workbench.admin.application.model.converter;

import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVO;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import org.junit.jupiter.api.Test;

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
        ApplicationVO current = converter.toListVO(application, version, null);

        assertThat(legacy.isPublishedVersionReady()).isFalse();
        assertThat(current.isPublishedVersionReady()).isTrue();
    }
}
