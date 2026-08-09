package com.lingxi.workbench.admin.workflow.service;

import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.admin.intent.service.IntentTreeService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.IntentClassifierNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowDraftResolverTest {

    private final AgentReferenceService agentReferenceService = mock(AgentReferenceService.class);
    private final IntentTreeService intentTreeService = mock(IntentTreeService.class);
    private final WorkflowDraftResolver resolver = new WorkflowDraftResolver(agentReferenceService, intentTreeService);

    @Test
    void shouldLockPublishedAgentAndIntentTreeVersions() {
        stubPublishedResources();

        WorkflowDefinition resolved = resolver.resolveForPublish(draftDefinition());

        AgentNodeConfig agent = (AgentNodeConfig) findNode(resolved, "agent").config();
        IntentClassifierNodeConfig intent = (IntentClassifierNodeConfig) findNode(resolved, "intent").config();
        assertThat(agent.agentVersionId()).isEqualTo(12L);
        assertThat(intent.intentTreeVersionId()).isEqualTo(22L);
    }

    @Test
    void shouldRejectIntentTreeWithoutPublishedVersion() {
        when(intentTreeService.findReference(20L))
                .thenReturn(Optional.of(new IntentTreeService.IntentTreeReference(20L, true, null, "售后意图")));

        assertThatThrownBy(() -> resolver.resolveForPublish(draftDefinition()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的意图树正式版本");
    }

    private void stubPublishedResources() {
        AgentReferenceService.AgentIdentity identity = new AgentReferenceService.AgentIdentity(10L, "售后Agent", "处理售后请求", AgentType.REACT);
        when(agentReferenceService.findAgent(10L))
                .thenReturn(Optional.of(new AgentReferenceService.AgentReference(identity, true, 12L)));
        when(agentReferenceService.findVersion(10L, 12L)).thenReturn(Optional.of(12L));
        when(intentTreeService.findReference(20L))
                .thenReturn(Optional.of(new IntentTreeService.IntentTreeReference(20L, true, 22L, "售后意图")));
        when(intentTreeService.findVersion(20L, 22L)).thenReturn(Optional.of(new IntentTreeDefinition(
                1, 30L, new IntentRoutingPolicy(5, new BigDecimal("0.65"), new BigDecimal("0.08")), List.of())));
    }

    private static WorkflowDefinition draftDefinition() {
        List<WorkflowNodeDefinition> nodes = List.of(
                new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                new WorkflowNodeDefinition("intent", "识别意图", WorkflowNodeType.INTENT_CLASSIFIER,
                        new IntentClassifierNodeConfig(20L, null, "query", Map.of("status", "intentStatus"))),
                new WorkflowNodeDefinition("agent", "售后Agent", WorkflowNodeType.AGENT,
                        new AgentNodeConfig(10L, null, Map.of("query", "query"), Map.of("answer", "answer"))),
                new WorkflowNodeDefinition("end", "结束", WorkflowNodeType.END, new EndNodeConfig(Map.of("response", "answer"))));
        return new WorkflowDefinition(1, "after_sales", nodes, List.of(
                WorkflowEdgeDefinition.direct("start", "intent"),
                WorkflowEdgeDefinition.direct("intent", "agent"),
                WorkflowEdgeDefinition.direct("agent", "end")));
    }

    private static WorkflowNodeDefinition findNode(WorkflowDefinition definition, String nodeId) {
        return definition.nodes().stream().filter(node -> node.id().equals(nodeId)).findFirst().orElseThrow();
    }
}
