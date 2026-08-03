package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationLimits;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
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

    @Test
    void shouldResolveSupervisorAndMemberPublishedVersions() {
        gateway.addAgent(10L, true, 101L, AgentType.SUPERVISOR);
        gateway.addVersion(10L, 101L);
        gateway.addAgent(20L, true, 201L);
        gateway.addVersion(20L, 201L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true, new ApplicationAgentReference(10L, null, null, null), List.of(new ApplicationAgentReference(20L, null, null, null)), new AgentCollaborationLimits(3, 1));

        ApplicationDefinitionResolution result = validator.resolveForPublish(new ApplicationDefinition(2, definition(null).workflow(), collaboration));

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().collaboration().supervisor().agentVersionId()).isEqualTo(101L);
        assertThat(result.definition().collaboration().members().getFirst().agentVersionId()).isEqualTo(201L);
        assertThat(result.definition().collaboration().members().getFirst().name()).isEqualTo("Agent-20");
    }

    @Test
    void shouldRejectSupervisorDuplicatedAsMember() {
        gateway.addAgent(10L, true, 101L, AgentType.SUPERVISOR);
        gateway.addVersion(10L, 101L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true, new ApplicationAgentReference(10L, null, null, null), List.of(new ApplicationAgentReference(10L, null, null, null)), new AgentCollaborationLimits(3, 1));

        ApplicationDefinitionResolution result = validator.resolveForPublish(new ApplicationDefinition(2, definition(null).workflow(), collaboration));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code()).contains("SUPERVISOR_AS_MEMBER");
    }

    @Test
    void shouldRejectRagAgentUsedAsSupervisor() {
        gateway.addAgent(10L, true, 101L, AgentType.RAG_CHAT);
        gateway.addVersion(10L, 101L);
        gateway.addAgent(20L, true, 201L, AgentType.RAG_CHAT);
        gateway.addVersion(20L, 201L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true, new ApplicationAgentReference(10L, null, null, null), List.of(new ApplicationAgentReference(20L, null, null, null)), new AgentCollaborationLimits(3, 1));

        ApplicationDefinitionResolution result = validator.resolveForPublish(new ApplicationDefinition(2, definition(null).workflow(), collaboration));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code()).contains("COLLABORATION_AGENT_TYPE_INVALID");
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
            addAgent(id, active, publishedVersionId, AgentType.RAG_CHAT);
        }

        void addAgent(Long id, boolean active, Long publishedVersionId, AgentType agentType) {
            agents.put(id, new AgentReference(new AgentIdentity(id, "Agent-" + id, "Agent职责-" + id, agentType), active, publishedVersionId));
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
