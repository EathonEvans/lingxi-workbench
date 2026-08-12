package com.lingxi.workbench.admin.agent.model.converter;

import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionEntity;
import com.lingxi.workbench.admin.agent.model.vo.AgentVO;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.junit.jupiter.api.Test;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

/** Agent 管理端响应转换回归测试。 */
class AgentConverterTest {

    @Test
    void shouldMarkOnlyCurrentSchemaVersionAsReady() {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        agent.setAgentType(AgentType.REACT);
        agent.setPublishedVersionId(11L);
        AgentVersionEntity version = new AgentVersionEntity();
        version.setId(11L);
        version.setVersionNo(2);
        version.setSchemaVersion(AGENT_SCHEMA_VERSION - 1);
        version.setDefinitionSnapshot(AgentDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION - 1)
                .agentType(AgentType.REACT)
                .build());
        AgentConverter converter = new AgentConverter();

        AgentVO legacy = converter.toListVO(agent, version, null);
        version.setSchemaVersion(AGENT_SCHEMA_VERSION);
        version.getDefinitionSnapshot().setSchemaVersion(AGENT_SCHEMA_VERSION);
        AgentVO current = converter.toListVO(agent, version, null);

        assertThat(legacy.isPublishedVersionReady()).isFalse();
        assertThat(current.isPublishedVersionReady()).isTrue();
    }
}
