package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationDraftValidatorTest {
    private final StubAgentReferenceGateway gateway = new StubAgentReferenceGateway();
    private final ApplicationDraftValidator validator =
            new ApplicationDraftValidator(gateway);

    @Test
    void shouldLockCurrentPublishedAgentVersion() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);

        ApplicationDefinitionResolution result =
                validator.resolveForPublish(definition(null));

        assertThat(result.valid()).isTrue();
        AgentNodeConfig resolvedConfig = (AgentNodeConfig)
                result.definition().workflow().nodes().get(1).config();
        assertThat(resolvedConfig.agentId()).isEqualTo(10L);
        assertThat(resolvedConfig.agentVersionId()).isEqualTo(101L);
    }

    @Test
    void shouldKeepExplicitHistoricalVersion() {
        gateway.addAgent(10L, true, 102L);
        gateway.addVersion(10L, 101L);

        ApplicationDefinitionResolution result =
                validator.resolveForPublish(definition(101L));

        assertThat(result.valid()).isTrue();
        AgentNodeConfig resolvedConfig = (AgentNodeConfig)
                result.definition().workflow().nodes().get(1).config();
        assertThat(resolvedConfig.agentVersionId()).isEqualTo(101L);
    }

    @Test
    void shouldRejectDisabledAgent() {
        gateway.addAgent(10L, false, 101L);

        ApplicationDefinitionResolution result =
                validator.resolveForPublish(definition(null));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .extracting(issue -> issue.code())
                .containsExactly("AGENT_DISABLED");
    }

    @Test
    void shouldRejectVersionOwnedByAnotherAgent() {
        gateway.addAgent(10L, true, 101L);

        ApplicationDefinitionResolution result =
                validator.resolveForPublish(definition(999L));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .extracting(issue -> issue.code())
                .containsExactly("AGENT_VERSION_NOT_FOUND");
    }

    private static ApplicationDefinition definition(Long versionId) {
        WorkflowDefinition workflow = new WorkflowDefinition(
                1,
                "main",
                List.of(
                        new WorkflowNodeDefinition(
                                "start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                        new WorkflowNodeDefinition(
                                "agent",
                                "问答Agent",
                                WorkflowNodeType.AGENT,
                                new AgentNodeConfig(
                                        10L,
                                        versionId,
                                        Map.of("query", "query"),
                                        Map.of("answer", "answer"))),
                        new WorkflowNodeDefinition(
                                "end",
                                "结束",
                                WorkflowNodeType.END,
                                new EndNodeConfig(Map.of("response", "answer")))),
                List.of(
                        WorkflowEdgeDefinition.direct("start", "agent"),
                        WorkflowEdgeDefinition.direct("agent", "end")));
        return new ApplicationDefinition(1, workflow);
    }

    private static final class StubAgentReferenceGateway
            implements ApplicationAgentReferenceGateway {
        private final Map<Long, AgentReference> agents = new HashMap<>();
        private final Map<String, Long> versions = new HashMap<>();

        void addAgent(Long id, boolean active, Long publishedVersionId) {
            agents.put(id, new AgentReference(id, active, publishedVersionId));
        }

        void addVersion(Long agentId, Long versionId) {
            versions.put(key(agentId, versionId), versionId);
        }

        @Override
        public Optional<AgentReference> findAgent(Long agentId) {
            return Optional.ofNullable(agents.get(agentId));
        }

        @Override
        public Optional<Long> findVersion(Long agentId, Long versionId) {
            return Optional.ofNullable(versions.get(key(agentId, versionId)));
        }

        private static String key(Long agentId, Long versionId) {
            return agentId + ":" + versionId;
        }
    }
}
