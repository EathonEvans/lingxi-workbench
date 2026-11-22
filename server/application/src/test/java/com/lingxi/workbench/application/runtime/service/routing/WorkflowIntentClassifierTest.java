package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeStatus;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowIntentClassifierTest {

    private final IntentCandidateRetriever candidateRetriever = mock(IntentCandidateRetriever.class);
    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo = mock(IntentTreeRuntimeRepo.class);
    private final IntentSemanticDecisionService semanticDecisionService = mock(IntentSemanticDecisionService.class);
    private final WorkflowIntentClassifier invoker = new WorkflowIntentClassifier(
            candidateRetriever, intentTreeRuntimeRepo, semanticDecisionService);

    @Test
    void shouldProjectSharedSemanticDecisionToWorkflowV2Contract() {
        RuntimeIntentTreeVersionEntity version = version();
        IntentCandidate refund = candidate("refund", 0.92D);
        IntentCandidate exchange = candidate("exchange", 0.86D);
        ApplicationRun run = run();
        ApplicationExecutionBudget executionBudget = mock(ApplicationExecutionBudget.class);
        when(run.budget()).thenReturn(executionBudget);
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version));
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(
                List.of(refund, exchange), List.of(refund, exchange)));
        when(semanticDecisionService.decideForWorkflow(any(), eq(version), any(), eq(executionBudget)))
                .thenReturn(IntentSemanticDecisionResult.builder()
                        .decision(IntentSemanticDecision.builder()
                                .action(IntentSemanticAction.SELECT)
                                .selectedIntentCode("refund")
                                .reasonCode(IntentDecisionReason.SINGLE_INTENT)
                                .build())
                        .modelInstanceId(31L)
                        .promptVersionId(32L)
                        .tokenUsage(new TokenUsage(8, 3, 11))
                        .modelCalls(1)
                        .retryCount(0)
                        .durationMillis(15L)
                        .build());

        IntentClassifierNodeResult result = invoker.invoke(invocation(), run);

        assertThat(result.getStatus()).isEqualTo(IntentClassifierNodeStatus.SELECTED);
        assertThat(result.getIntentCode()).isEqualTo("refund");
        assertThat(result.getReasonCode()).isEqualTo("SINGLE_INTENT");
        assertThat(result.getSimilarity()).isEqualTo(0.92D);
        assertThat(result.getCandidateCodes()).containsExactly("refund", "exchange");
        assertThat(result.getModelInstanceId()).isEqualTo(31L);
        assertThat(result.getPromptVersionId()).isEqualTo(32L);
        assertThat(result.getTokenUsage().totalTokens()).isEqualTo(11);
    }

    @Test
    void shouldDeferNoCandidateWithoutCallingSemanticModel() {
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version()));
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(List.of(), List.of()));

        IntentClassifierNodeResult result = invoker.invoke(invocation(), run());

        assertThat(result.getStatus()).isEqualTo(IntentClassifierNodeStatus.DEFERRED);
        assertThat(result.getReasonCode()).isEqualTo("NO_SUITABLE_CANDIDATE");
        assertThat(result.getCandidateCodes()).isEmpty();
        assertThat(result.getIntentCode()).isNull();
        assertThat(result.getSimilarity()).isNull();
        assertThat(result.getModelCalls()).isZero();
        verify(semanticDecisionService, never()).decideForWorkflow(any(), any(), any(), any());
    }

    private static ApplicationRun run() {
        ApplicationRun run = mock(ApplicationRun.class);
        ConversationContext conversationContext = mock(ConversationContext.class);
        when(run.conversation()).thenReturn(conversationContext);
        when(conversationContext.forkForQuery("我要退款")).thenReturn(conversationContext);
        return run;
    }

    private static RuntimeIntentTreeVersionEntity version() {
        IntentRoutingPolicy policy = IntentRoutingPolicy.builder()
                .topK(5)
                .recallThreshold(new BigDecimal("0.65"))
                .chatModelInstanceId(31L)
                .decisionPromptVersionId(32L)
                .maxOutputTokens(256)
                .timeoutSeconds(10)
                .maxRepeatAttempts(1)
                .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                .build();
        RuntimeIntentTreeVersionEntity version = new RuntimeIntentTreeVersionEntity();
        version.setId(40L);
        version.setIntentTreeId(30L);
        version.setDefinitionSnapshot(new IntentTreeDefinition(2, 33L, policy, List.of()));
        return version;
    }

    private static IntentClassifierNodeInvocation invocation() {
        return IntentClassifierNodeInvocation.builder()
                .executionId("execution-1")
                .runId("run-1")
                .workflowVersionId(20L)
                .nodeId("intent")
                .intentTreeId(30L)
                .intentTreeVersionId(40L)
                .query("我要退款")
                .build();
    }

    private static IntentCandidate candidate(String intentCode, double similarity) {
        return IntentCandidate.builder()
                .intentCode(intentCode)
                .similarity(similarity)
                .build();
    }
}
