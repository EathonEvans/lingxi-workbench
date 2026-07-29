package com.lingxi.workbench.workflow.engine.langgraph;

import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.model.ConditionOperator;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.WorkflowRoute;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.ConditionNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangGraphWorkflowExecutorTest {

    private static final long CLASSIFIER_VERSION_ID = 101L;
    private static final long KNOWLEDGE_VERSION_ID = 102L;
    private static final long FALLBACK_VERSION_ID = 103L;

    @Test
    void shouldExecuteMatchedAgentBranchAndExposeTrace() {
        List<AgentNodeInvocation> invocations = new ArrayList<>();
        LangGraphWorkflowExecutor executor = new LangGraphWorkflowExecutor(fakeAgentInvoker(invocations));

        WorkflowExecutionResult result = executor.execute(
                workflowDefinition(),
                new WorkflowExecutionRequest("run-001", "app-version-1", Map.of("query", "如何配置知识库")));

        assertThat(result.outputs()).containsEntry("response", "知识库回答：如何配置知识库");
        assertThat(invocations)
                .extracting(AgentNodeInvocation::getAgentVersionId)
                .containsExactly(CLASSIFIER_VERSION_ID, KNOWLEDGE_VERSION_ID);
        assertThat(invocations)
                .extracting(AgentNodeInvocation::getApplicationVersionId)
                .containsOnly("app-version-1");
        assertThat(result.traces())
                .extracting(WorkflowNodeTrace::getNodeId)
                .containsExactly("start", "classifier", "route", "knowledge-agent", "end");
        assertThat(result.traces())
                .filteredOn(trace -> trace.getNodeId().equals("route"))
                .singleElement()
                .satisfies(trace -> assertThat(trace.getInputs())
                        .containsEntry("intent", "KNOWLEDGE_QA"));
        assertThat(result.traces()).allMatch(
                trace -> trace.getErrorMessage() == null);
    }

    @Test
    void shouldExecuteNotMatchedAgentBranch() {
        List<AgentNodeInvocation> invocations = new ArrayList<>();
        LangGraphWorkflowExecutor executor = new LangGraphWorkflowExecutor(fakeAgentInvoker(invocations));

        WorkflowExecutionResult result = executor.execute(
                workflowDefinition(),
                new WorkflowExecutionRequest("run-002", "app-version-1", Map.of("query", "今天天气怎么样")));

        assertThat(result.outputs()).containsEntry("response", "通用回答：今天天气怎么样");
        assertThat(invocations)
                .extracting(AgentNodeInvocation::getAgentVersionId)
                .containsExactly(CLASSIFIER_VERSION_ID, FALLBACK_VERSION_ID);
        assertThat(result.traces())
                .extracting(WorkflowNodeTrace::getNodeId)
                .containsExactly("start", "classifier", "route", "fallback-agent", "end");
    }

    @Test
    void shouldRejectAgentNodeWithoutLockedVersion() {
        WorkflowDefinition definition = workflowDefinition();
        List<WorkflowNodeDefinition> invalidNodes = definition.nodes().stream()
                .map(node -> node.id().equals("classifier")
                        ? new WorkflowNodeDefinition(
                                node.id(),
                                node.name(),
                                node.type(),
                                new AgentNodeConfig(
                                        "1",
                                        null,
                                        Map.of("query", "query"),
                                        Map.of("intent", "intent")))
                        : node)
                .toList();

        LangGraphWorkflowExecutor executor =
                new LangGraphWorkflowExecutor(invocation -> Map.of());

        assertThatThrownBy(() -> executor.execute(
                        new WorkflowDefinition(1, definition.code(), invalidNodes, definition.edges()),
                        new WorkflowExecutionRequest("run-003", "app-version-1", Map.of("query", "测试"))))
                .isInstanceOf(WorkflowDefinitionException.class)
                .hasMessageContaining("agentVersionId");
    }

    private AgentNodeInvoker fakeAgentInvoker(List<AgentNodeInvocation> invocations) {
        return invocation -> {
            invocations.add(invocation);
            String query = String.valueOf(invocation.getInputs().get("query"));
            if (invocation.getAgentVersionId() == CLASSIFIER_VERSION_ID) {
                String intent = query.contains("知识库") ? "KNOWLEDGE_QA" : "GENERAL";
                return Map.of("intent", intent);
            }
            if (invocation.getAgentVersionId() == KNOWLEDGE_VERSION_ID) {
                return Map.of("answer", "知识库回答：" + query);
            }
            if (invocation.getAgentVersionId() == FALLBACK_VERSION_ID) {
                return Map.of("answer", "通用回答：" + query);
            }
            throw new IllegalArgumentException("未知Agent版本");
        };
    }

    private WorkflowDefinition workflowDefinition() {
        List<WorkflowNodeDefinition> nodes = List.of(
                new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                new WorkflowNodeDefinition(
                        "classifier",
                        "意图分类Agent",
                        WorkflowNodeType.AGENT,
                        new AgentNodeConfig(
                                "1",
                                String.valueOf(CLASSIFIER_VERSION_ID),
                                Map.of("query", "query"),
                                Map.of("intent", "intent"))),
                new WorkflowNodeDefinition(
                        "route",
                        "知识问答路由",
                        WorkflowNodeType.CONDITION,
                        new ConditionNodeConfig("intent", ConditionOperator.EQUALS, "KNOWLEDGE_QA")),
                new WorkflowNodeDefinition(
                        "knowledge-agent",
                        "知识库Agent",
                        WorkflowNodeType.AGENT,
                        new AgentNodeConfig(
                                "2",
                                String.valueOf(KNOWLEDGE_VERSION_ID),
                                Map.of("query", "query"),
                                Map.of("answer", "answer"))),
                new WorkflowNodeDefinition(
                        "fallback-agent",
                        "通用Agent",
                        WorkflowNodeType.AGENT,
                        new AgentNodeConfig(
                                "3",
                                String.valueOf(FALLBACK_VERSION_ID),
                                Map.of("query", "query"),
                                Map.of("answer", "answer"))),
                new WorkflowNodeDefinition(
                        "end",
                        "结束",
                        WorkflowNodeType.END,
                        new EndNodeConfig(Map.of("response", "answer"))));

        List<WorkflowEdgeDefinition> edges = List.of(
                WorkflowEdgeDefinition.direct("start", "classifier"),
                WorkflowEdgeDefinition.direct("classifier", "route"),
                WorkflowEdgeDefinition.routed("route", "knowledge-agent", WorkflowRoute.MATCHED),
                WorkflowEdgeDefinition.routed("route", "fallback-agent", WorkflowRoute.NOT_MATCHED),
                WorkflowEdgeDefinition.direct("knowledge-agent", "end"),
                WorkflowEdgeDefinition.direct("fallback-agent", "end"));

        return new WorkflowDefinition(1, "knowledge-routing", nodes, edges);
    }
}
