package com.lingxi.workbench.workflow.engine.langgraph;

import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.exception.IntentClassifierNodeException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.model.ConditionOperator;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionPorts;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.WorkflowRoute;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.ConditionNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.IntentClassifierNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import com.lingxi.workbench.workflow.service.IntentClassifierNodeInvoker;
import com.lingxi.workbench.workflow.service.WorkflowRunListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangGraphWorkflowExecutorTest {

    private static final long CLASSIFIER_VERSION_ID = 101L;
    private static final long KNOWLEDGE_VERSION_ID = 102L;
    private static final long FALLBACK_VERSION_ID = 103L;

    @Test
    void shouldExecuteMatchedAgentBranchAndExposeTrace() {
        List<AgentNodeInvocation> invocations = new ArrayList<>();
        LangGraphWorkflowExecutor executor = new LangGraphWorkflowExecutor();

        WorkflowExecutionResult result = executor.execute(
                workflowDefinition(),
                new WorkflowExecutionRequest("run-001", 10L, 11L, Map.of("query", "如何配置知识库")),
                ports(fakeAgentInvoker(invocations), invocation -> deferredIntentResult()));

        assertThat(result.outputs()).containsEntry("response", "知识库回答：如何配置知识库");
        assertThat(invocations)
                .extracting(AgentNodeInvocation::getAgentVersionId)
                .containsExactly(CLASSIFIER_VERSION_ID, KNOWLEDGE_VERSION_ID);
        assertThat(invocations)
                .extracting(AgentNodeInvocation::getWorkflowVersionId)
                .containsOnly(11L);
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
        LangGraphWorkflowExecutor executor = new LangGraphWorkflowExecutor();

        WorkflowExecutionResult result = executor.execute(
                workflowDefinition(),
                new WorkflowExecutionRequest("run-002", 10L, 11L, Map.of("query", "今天天气怎么样")),
                ports(fakeAgentInvoker(invocations), invocation -> deferredIntentResult()));

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
                                        1L,
                                        null,
                                        Map.of("query", "query"),
                                        Map.of("intent", "intent")))
                        : node)
                .toList();

        LangGraphWorkflowExecutor executor = new LangGraphWorkflowExecutor();

        assertThatThrownBy(() -> executor.execute(
                        new WorkflowDefinition(2, definition.code(), invalidNodes, definition.edges()),
                        new WorkflowExecutionRequest("run-003", 10L, 11L, Map.of("query", "测试")),
                        ports(invocation -> Map.of(), invocation -> deferredIntentResult())))
                .isInstanceOf(WorkflowDefinitionException.class)
                .hasMessageContaining("agentVersionId");
    }

    @Test
    void shouldExecuteIntentClassifierAndMapStandardOutputs() {
        List<IntentClassifierNodeInvocation> invocations = new ArrayList<>();
        LangGraphWorkflowExecutor executor = new LangGraphWorkflowExecutor();
        WorkflowExecutionPorts ports = ports(invocation -> Map.of("answer", "退款处理完成"), invocation -> {
                    invocations.add(invocation);
                    return IntentClassifierNodeResult.builder()
                            .status(IntentClassifierNodeStatus.SELECTED)
                            .intentCode("refund")
                            .reasonCode("SINGLE_INTENT")
                            .similarity(0.93D)
                            .candidateCodes(List.of("refund"))
                            .recognizedSlots(List.of())
                            .modelInstanceId(91L)
                            .promptVersionId(92L)
                            .tokenUsage(new TokenUsage(8, 3, 11))
                            .modelCalls(1)
                            .retryCount(0)
                            .durationMillis(12L)
                            .build();
                });

        WorkflowExecutionResult result = executor.execute(intentWorkflowDefinition(),
                new WorkflowExecutionRequest("run-intent", 20L, 21L, Map.of("query", "我要退款")), ports);

        assertThat(invocations).singleElement().satisfies(invocation -> {
            assertThat(invocation.getIntentTreeId()).isEqualTo(30L);
            assertThat(invocation.getIntentTreeVersionId()).isEqualTo(31L);
            assertThat(invocation.getQuery()).isEqualTo("我要退款");
            assertThat(invocation.getRunId()).isNotBlank();
            assertThat(invocation.getWorkflowVersionId()).isEqualTo(21L);
        });
        assertThat(result.outputs()).containsEntry("response", "退款处理完成");
        assertThat(result.traces()).filteredOn(trace -> trace.getNodeId().equals("intent"))
                .singleElement().satisfies(trace -> {
                    assertThat(trace.getOutputs())
                            .containsEntry("intentCode", "refund")
                            .containsEntry("intentStatus", "SELECTED")
                            .containsEntry("intentReasonCode", "SINGLE_INTENT")
                            .containsEntry("intentSimilarity", 0.93D);
                    assertThat(trace.getDecisionStatus()).isEqualTo(IntentClassifierNodeStatus.SELECTED);
                    assertThat(trace.getReasonCode()).isEqualTo("SINGLE_INTENT");
                    assertThat(trace.getModelInstanceId()).isEqualTo(91L);
                    assertThat(trace.getPromptVersionId()).isEqualTo(92L);
                    assertThat(trace.getTokenUsage().totalTokens()).isEqualTo(11);
                    assertThat(trace.getModelCalls()).isEqualTo(1);
                });
    }

    @Test
    void shouldExposeSafeModelFactsWhenIntentClassifierFails() {
        IntentClassifierNodeResult failedResult = IntentClassifierNodeResult.builder()
                .status(IntentClassifierNodeStatus.DEFERRED)
                .reasonCode("SEMANTIC_DECISION_FAILED")
                .candidateCodes(List.of("refund"))
                .recognizedSlots(List.of())
                .modelInstanceId(91L)
                .promptVersionId(92L)
                .tokenUsage(new TokenUsage(8, 3, 11))
                .modelCalls(1)
                .retryCount(0)
                .durationMillis(15L)
                .build();
        AtomicReference<WorkflowNodeTrace> failedTrace = new AtomicReference<>();
        WorkflowRunListener listener = new WorkflowRunListener() {
            @Override
            public void onNodeFailed(com.lingxi.workbench.workflow.model.WorkflowRunContext context,
                                     WorkflowNodeDefinition node, WorkflowNodeTrace trace) {
                failedTrace.set(trace);
            }
        };
        WorkflowExecutionPorts ports = new WorkflowExecutionPorts(
                invocation -> Map.of("answer", "不应执行"),
                invocation -> {
                    throw new IntentClassifierNodeException("分类失败", failedResult, new IllegalStateException("模型超时"));
                },
                listener);

        assertThatThrownBy(() -> new LangGraphWorkflowExecutor().execute(
                intentWorkflowDefinition(),
                new WorkflowExecutionRequest("run-failed", 20L, 21L, Map.of("query", "我要退款")),
                ports)).hasMessageContaining("intent-routing");

        assertThat(failedTrace.get()).satisfies(trace -> {
            assertThat(trace.getStatus()).isEqualTo(com.lingxi.workbench.workflow.model.WorkflowNodeStatus.FAILED);
            assertThat(trace.getDecisionStatus()).isEqualTo(IntentClassifierNodeStatus.DEFERRED);
            assertThat(trace.getReasonCode()).isEqualTo("SEMANTIC_DECISION_FAILED");
            assertThat(trace.getModelInstanceId()).isEqualTo(91L);
            assertThat(trace.getPromptVersionId()).isEqualTo(92L);
            assertThat(trace.getTokenUsage().totalTokens()).isEqualTo(11);
            assertThat(trace.getModelCalls()).isEqualTo(1);
            assertThat(trace.getOutputs()).isEmpty();
        });
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

    private WorkflowExecutionPorts ports(AgentNodeInvoker agentNodeInvoker,
                                         IntentClassifierNodeInvoker intentClassifierNodeInvoker) {
        return new WorkflowExecutionPorts(agentNodeInvoker, intentClassifierNodeInvoker, null);
    }

    private WorkflowDefinition workflowDefinition() {
        List<WorkflowNodeDefinition> nodes = List.of(
                new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                new WorkflowNodeDefinition(
                        "classifier",
                        "意图分类Agent",
                        WorkflowNodeType.AGENT,
                        new AgentNodeConfig(
                                1L,
                                CLASSIFIER_VERSION_ID,
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
                                2L,
                                KNOWLEDGE_VERSION_ID,
                                Map.of("query", "query"),
                                Map.of("answer", "answer"))),
                new WorkflowNodeDefinition(
                        "fallback-agent",
                        "通用Agent",
                        WorkflowNodeType.AGENT,
                        new AgentNodeConfig(
                                3L,
                                FALLBACK_VERSION_ID,
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

        return new WorkflowDefinition(2, "knowledge-routing", nodes, edges);
    }

    private WorkflowDefinition intentWorkflowDefinition() {
        List<WorkflowNodeDefinition> nodes = List.of(
                new WorkflowNodeDefinition("start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                new WorkflowNodeDefinition("intent", "识别意图", WorkflowNodeType.INTENT_CLASSIFIER,
                        new IntentClassifierNodeConfig(30L, 31L, "query", Map.of(
                                "status", "intentStatus", "intentCode", "intentCode",
                                "reasonCode", "intentReasonCode", "similarity", "intentSimilarity",
                                "candidateCodes", "intentCandidates", "recognizedSlots", "intentSlots"))),
                new WorkflowNodeDefinition("route", "退款分支", WorkflowNodeType.CONDITION,
                        new ConditionNodeConfig("intentCode", ConditionOperator.EQUALS, "refund")),
                new WorkflowNodeDefinition("refund-agent", "退款Agent", WorkflowNodeType.AGENT,
                        new AgentNodeConfig(40L, 41L, Map.of("query", "query"), Map.of("answer", "answer"))),
                new WorkflowNodeDefinition("fallback-agent", "兜底Agent", WorkflowNodeType.AGENT,
                        new AgentNodeConfig(42L, 43L, Map.of("query", "query"), Map.of("answer", "answer"))),
                new WorkflowNodeDefinition("end", "结束", WorkflowNodeType.END, new EndNodeConfig(Map.of("response", "answer"))));
        List<WorkflowEdgeDefinition> edges = List.of(
                WorkflowEdgeDefinition.direct("start", "intent"),
                WorkflowEdgeDefinition.direct("intent", "route"),
                WorkflowEdgeDefinition.routed("route", "refund-agent", WorkflowRoute.MATCHED),
                WorkflowEdgeDefinition.routed("route", "fallback-agent", WorkflowRoute.NOT_MATCHED),
                WorkflowEdgeDefinition.direct("refund-agent", "end"),
                WorkflowEdgeDefinition.direct("fallback-agent", "end"));
        return new WorkflowDefinition(2, "intent-routing", nodes, edges);
    }

    private static IntentClassifierNodeResult deferredIntentResult() {
        return IntentClassifierNodeResult.builder()
                .status(IntentClassifierNodeStatus.DEFERRED)
                .reasonCode("NO_SUITABLE_CANDIDATE")
                .candidateCodes(List.of())
                .recognizedSlots(List.of())
                .tokenUsage(TokenUsage.empty())
                .build();
    }
}
