package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;
import com.lingxi.workbench.application.runtime.service.routing.IntentCandidateRetriever;
import com.lingxi.workbench.application.runtime.service.routing.IntentSemanticDecisionService;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class IntentRoutingPreviewServiceTest {

    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo = mock(IntentTreeRuntimeRepo.class);
    private final ApplicationRuntimeRepo applicationRuntimeRepo = mock(ApplicationRuntimeRepo.class);
    private final IntentCandidateRetriever candidateRetriever = mock(IntentCandidateRetriever.class);
    private final IntentSemanticDecisionService semanticDecisionService = mock(IntentSemanticDecisionService.class);
    private final RuntimeVariablesInitializer runtimeVariablesInitializer = new RuntimeVariablesInitializer(
            Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC), "Lingxi", Locale.CHINA);
    private final IntentRoutingPreviewService service = new IntentRoutingPreviewService(
            intentTreeRuntimeRepo, applicationRuntimeRepo, candidateRetriever,
            semanticDecisionService, runtimeVariablesInitializer);

    @Test
    void shouldReturnSafeDeferWithoutCallingModelWhenRecallIsEmpty() {
        mockIntentTreeVersion();
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(List.of(), List.of()));

        IntentRoutingPreviewResult result = service.preview(request());

        assertThat(result.getCandidates()).isEmpty();
        assertThat(result.getSemanticAction()).isEqualTo(IntentSemanticAction.DEFER);
        assertThat(result.getFinalAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getFinalReasonCode()).isEqualTo(IntentRoutingReason.NO_CANDIDATE);
        assertThat(result.getModelCalls()).isZero();
        verify(semanticDecisionService, never()).decide(any(), any(), any());
    }

    @Test
    void shouldExposeDecisionEvidenceAndMappedActionWithoutExecutingCapability() {
        mockIntentTreeVersion();
        IntentCandidate candidate = IntentCandidate.builder()
                .intentCode("refund")
                .name("退款")
                .similarity(0.91D)
                .rank(1)
                .build();
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(
                List.of(candidate), List.of(candidate)));
        when(semanticDecisionService.decide(any(), any(), any())).thenReturn(IntentSemanticDecisionResult.builder()
                .decision(IntentSemanticDecision.builder()
                        .action(IntentSemanticAction.SELECT)
                        .selectedIntentCode("refund")
                        .reasonCode(IntentDecisionReason.SINGLE_INTENT)
                        .build())
                .modelInstanceId(51L)
                .promptVersionId(52L)
                .tokenUsage(TokenUsage.of(100, 20, null))
                .modelCalls(1)
                .retryCount(0)
                .durationMillis(120L)
                .build());

        IntentRoutingPreviewResult result = service.preview(request());

        assertThat(result.getCandidates()).singleElement().satisfies(item -> {
            assertThat(item.intentCode()).isEqualTo("refund");
            assertThat(item.similarity()).isEqualTo(0.91D);
        });
        assertThat(result.getFinalAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getFinalReasonCode()).isEqualTo(IntentRoutingReason.ROUTED_BY_SEMANTIC);
        assertThat(result.getTargetType()).isEqualTo(IntentExecutionTargetType.WORKFLOW);
        assertThat(result.getCapabilityCode()).isEqualTo("refund-workflow");
        assertThat(result.getTokenUsage().totalTokens()).isEqualTo(120);
        assertThat(result.getModelCalls()).isEqualTo(1);
        verify(semanticDecisionService).decide(any(), any(), any());
    }

    @Test
    void shouldDeferWhenSelectedCandidateHasNoLockedMapping() {
        mockIntentTreeVersion();
        IntentCandidate candidate = IntentCandidate.builder()
                .intentCode("complaint")
                .name("投诉")
                .similarity(0.89D)
                .rank(1)
                .build();
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(
                List.of(candidate), List.of(candidate)));
        when(semanticDecisionService.decide(any(), any(), any())).thenReturn(IntentSemanticDecisionResult.builder()
                .decision(IntentSemanticDecision.builder()
                        .action(IntentSemanticAction.SELECT)
                        .selectedIntentCode("complaint")
                        .reasonCode(IntentDecisionReason.SINGLE_INTENT)
                        .build())
                .tokenUsage(TokenUsage.empty())
                .modelCalls(1)
                .build());

        IntentRoutingPreviewResult result = service.preview(request());

        assertThat(result.getFinalAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getFinalReasonCode()).isEqualTo(IntentRoutingReason.UNMAPPED_CANDIDATE);
        assertThat(result.getTargetType()).isNull();
        assertThat(result.getCapabilityCode()).isNull();
    }

    @Test
    void shouldLoadImmutableApplicationVersionForOfflineEvaluation() {
        mockIntentTreeVersion();
        RuntimeApplicationEntity application = new RuntimeApplicationEntity();
        application.setId(10L);
        application.setCode("refund-assistant");
        RuntimeApplicationVersionEntity version = new RuntimeApplicationVersionEntity();
        version.setId(20L);
        version.setApplicationId(10L);
        version.setDefinitionSnapshot(request().definition());
        when(applicationRuntimeRepo.findById(10L)).thenReturn(Optional.of(application));
        when(applicationRuntimeRepo.findVersion(10L, 20L)).thenReturn(Optional.of(version));
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(List.of(), List.of()));

        IntentRoutingPreviewResult result = service.previewVersion(
                new IntentRoutingVersionPreviewRequest(10L, 20L, "目标外问题"));

        assertThat(result.getFinalAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getModelInstanceId()).isEqualTo(51L);
        assertThat(result.getPromptVersionId()).isEqualTo(52L);
    }

    @Test
    void shouldRejectLockedIntentTreeWithoutRoutingPolicy() {
        RuntimeIntentTreeVersionEntity version = new RuntimeIntentTreeVersionEntity();
        version.setId(40L);
        version.setIntentTreeId(30L);
        version.setDefinitionSnapshot(new IntentTreeDefinition(2, 50L, null, List.of()));
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.preview(request()))
                .hasMessage("Application锁定的IntentTreeVersion缺少路由策略");
        verify(candidateRetriever, never()).retrieve(any());
    }

    private void mockIntentTreeVersion() {
        RuntimeIntentTreeVersionEntity version = new RuntimeIntentTreeVersionEntity();
        version.setId(40L);
        version.setIntentTreeId(30L);
        version.setDefinitionSnapshot(new IntentTreeDefinition(2, 50L,
                IntentRoutingPolicy.builder()
                        .chatModelInstanceId(51L)
                        .decisionPromptVersionId(52L)
                        .build(), List.of()));
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version));
    }

    private static IntentRoutingPreviewRequest request() {
        ApplicationCapabilityDefinition fallback = capability("fallback-agent", ApplicationCapabilityType.AGENT, 1L, 2L);
        ApplicationCapabilityDefinition workflow = capability(
                "refund-workflow", ApplicationCapabilityType.WORKFLOW, 3L, 4L);
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(1L, 2L, "默认助手", "负责收口"),
                        List.of(), List.of(), List.of()))
                .memoryConf(MemoryConfig.disabled())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("fallback-agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(fallback, workflow))
                        .build())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .intentRouting(ApplicationIntentBinding.builder()
                        .enabled(true)
                        .intentTreeId(30L)
                        .intentTreeVersionId(40L)
                        .mappings(List.of(ApplicationIntentMapping.builder()
                                .intentCode("refund")
                                .targetType(IntentExecutionTargetType.WORKFLOW)
                                .capabilityCode("refund-workflow")
                                .targetId(3L)
                                .targetVersionId(4L)
                                .build()))
                        .build())
                .build();
        return new IntentRoutingPreviewRequest(10L, "refund-assistant", "我要退款", definition);
    }

    private static ApplicationCapabilityDefinition capability(
            String code, ApplicationCapabilityType type, Long targetId, Long targetVersionId) {
        return ApplicationCapabilityDefinition.builder()
                .code(code)
                .type(type)
                .targetId(targetId)
                .targetVersionId(targetVersionId)
                .build();
    }
}
