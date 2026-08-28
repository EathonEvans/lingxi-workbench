package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentRoutingEvaluationServiceTest {

    private final IntentEvaluationService baselineService = mock(IntentEvaluationService.class);
    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo = mock(IntentTreeRuntimeRepo.class);
    private final IntentRoutingVersionPreviewGateway previewGateway = mock(IntentRoutingVersionPreviewGateway.class);
    private final IntentRoutingEvaluationService service = new IntentRoutingEvaluationService(
            baselineService, intentTreeRuntimeRepo, previewGateway);

    @Test
    void shouldBuildPassingReportFromThreeHundredHumanLabeledSamples() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        mockPublishedContract(dataset);
        when(previewGateway.preview(anyLong(), anyLong(), anyString()))
                .thenAnswer(invocation -> resultFor(invocation.getArgument(2, String.class)));

        IntentRoutingEvaluationReport report = service.evaluate(dataset);

        assertThat(report.getSampleCount()).isEqualTo(300);
        assertThat(report.isProductionGatePassed()).isTrue();
        assertThat(report.getMetrics().getCandidateRecallAtK()).isEqualTo(1D);
        assertThat(report.getMetrics().getExecutablePrecision()).isEqualTo(1D);
        assertThat(report.getMetrics().getOutOfScopeDeferRate()).isEqualTo(1D);
        assertThat(report.getMetrics().getMultiIntentSafeRate()).isEqualTo(1D);
        assertThat(report.getMetrics().getHighRiskWrongDirectRate()).isZero();
        assertThat(report.getFailures()).isEmpty();
        assertThat(report.getConfusionMatrix()).anySatisfy(entry -> {
            assertThat(entry.expectedAction()).isEqualTo(IntentRoutingAction.EXECUTE_AGENT);
            assertThat(entry.actualAction()).isEqualTo(IntentRoutingAction.EXECUTE_AGENT);
            assertThat(entry.count()).isPositive();
        });
    }

    @Test
    void shouldBuildReportFromExternallyObservedResultsWithoutCallingModelAgain() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        mockPublishedContract(dataset);
        List<IntentRoutingEvaluatedSample> results = dataset.samples().stream()
                .map(sample -> new IntentRoutingEvaluatedSample(sample.getId(), resultFor(sample.getQuery())))
                .toList();

        IntentRoutingEvaluationReport report = service.report(dataset, results);

        assertThat(report.isProductionGatePassed()).isTrue();
        verify(previewGateway, never()).preview(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldExposeFailureWithoutCopyingQueryIntoReport() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        mockPublishedContract(dataset);
        when(previewGateway.preview(anyLong(), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(2, String.class);
                    if ("sample-0".equals(query)) {
                        return preview(IntentRoutingAction.DEFER_TO_AGENT, null, List.of());
                    }
                    return resultFor(query);
                });

        IntentRoutingEvaluationReport report = service.evaluate(dataset);

        assertThat(report.getFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.getSampleId()).isEqualTo("sample-0");
            assertThat(failure.getExpectedAction()).isEqualTo(IntentRoutingAction.EXECUTE_AGENT);
            assertThat(failure.getActualAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        });
    }

    @Test
    void shouldRejectDatasetBelowProductionMinimum() {
        IntentRoutingEvaluationDataset dataset = validDataset(299);

        assertThatThrownBy(() -> service.evaluate(dataset))
                .hasMessage("V2生产门禁样本不得少于300条");
    }

    @Test
    void shouldRejectDatasetThatDoesNotMatchPublishedApplicationLock() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        when(baselineService.exportDatasetTemplate(10L, 20L, "v2-production-1"))
                .thenReturn(lockedDataset(dataset, 41L));

        assertThatThrownBy(() -> service.evaluate(dataset))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus()).isEqualTo(
                                HttpStatusEnum.CONFLICT))
                .hasMessageContaining("ApplicationVersion锁定版本不一致");
    }

    @Test
    void shouldRejectExpectedIntentCodeOutsidePublishedLeafSet() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        mockPublishedContract(dataset);
        dataset.getSamples().getFirst().setExpectedIntentCodes(List.of("retired-intent"));

        assertThatThrownBy(() -> service.evaluate(dataset))
                .hasMessage("样本包含当前发布意图树不存在的expectedIntentCode");
    }

    @Test
    void shouldRequireHighRiskExecutableCapabilitySample() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        dataset.getSamples().forEach(sample -> sample.setHighRisk(false));
        dataset.getSamples().stream()
                .filter(sample -> sample.getExpectedAction() == IntentRoutingAction.DEFER_TO_AGENT)
                .findFirst()
                .orElseThrow()
                .setHighRisk(true);

        assertThatThrownBy(() -> service.evaluate(dataset))
                .hasMessage("V2生产门禁数据集缺少高风险能力样本");
    }

    @Test
    void shouldKeepFinalActionAccuracyIndependentFromSelectedIntentAccuracy() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        mockPublishedContract(dataset);
        when(previewGateway.preview(anyLong(), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(2, String.class);
                    if ("sample-0".equals(query)) {
                        return preview(IntentRoutingAction.EXECUTE_AGENT, "wrong-intent", List.of());
                    }
                    return resultFor(query);
                });

        IntentRoutingEvaluationReport report = service.evaluate(dataset);

        assertThat(report.getMetrics().getFinalActionAccuracy()).isEqualTo(1D);
        assertThat(report.getMetrics().getSelectedIntentAccuracy()).isLessThan(1D);
        assertThat(report.getFailures()).hasSize(1);
    }

    @Test
    void shouldRejectIncompletePreviewResponse() {
        IntentRoutingEvaluationDataset dataset = validDataset(300);
        mockPublishedContract(dataset);
        when(previewGateway.preview(anyLong(), anyLong(), anyString()))
                .thenReturn(IntentRoutingPreviewResult.builder()
                        .modelInstanceId(51L)
                        .promptVersionId(52L)
                        .build());

        assertThatThrownBy(() -> service.evaluate(dataset))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus()).isEqualTo(
                                HttpStatusEnum.SERVICE_UNAVAILABLE))
                .hasMessage("V2意图评测响应协议不完整");
    }

    private static IntentRoutingEvaluationDataset validDataset(int count) {
        List<IntentRoutingEvaluationSample> samples = new ArrayList<>();
        IntentEvaluationScenario[] scenarios = IntentEvaluationScenario.values();
        for (int index = 0; index < count; index++) {
            IntentRoutingAction action = expectedAction(index);
            samples.add(IntentRoutingEvaluationSample.builder()
                    .id("sample-" + index)
                    .query("sample-" + index)
                    .scenario(scenarios[index % scenarios.length])
                    .expectedAction(action)
                    .expectedIntentCodes(action == IntentRoutingAction.DEFER_TO_AGENT
                            ? List.of() : List.of("intent-" + index))
                    .highRisk(index == 0)
                    .build());
        }
        List<IntentEvaluationLeafMetadata> leafMetadata = samples.stream()
                .flatMap(sample -> sample.expectedIntentCodes().stream())
                .distinct()
                .map(code -> IntentEvaluationLeafMetadata.builder().intentCode(code).name(code).build())
                .toList();
        return IntentRoutingEvaluationDataset.builder()
                .schemaVersion(2)
                .datasetVersion("v2-production-1")
                .labelingOwner("qa-team")
                .labeledAt(Instant.parse("2026-08-24T00:00:00Z"))
                .sourceDescription("真实历史请求与人工场景补采")
                .applicationId(10L)
                .applicationVersionId(20L)
                .intentTreeId(30L)
                .intentTreeVersionId(40L)
                .embeddingModelInstanceId(50L)
                .chatModelInstanceId(51L)
                .promptVersionId(52L)
                .leafMetadata(leafMetadata)
                .samples(samples)
                .build();
    }

    private void mockPublishedContract(IntentRoutingEvaluationDataset dataset) {
        when(baselineService.exportDatasetTemplate(10L, 20L, "v2-production-1"))
                .thenReturn(lockedDataset(dataset, 40L));
        RuntimeIntentTreeVersionEntity treeVersion = new RuntimeIntentTreeVersionEntity();
        treeVersion.setId(40L);
        treeVersion.setIntentTreeId(30L);
        treeVersion.setEmbeddingModelInstanceId(50L);
        treeVersion.setDefinitionSnapshot(new IntentTreeDefinition(2, 50L,
                IntentRoutingPolicy.builder()
                        .chatModelInstanceId(51L)
                        .decisionPromptVersionId(52L)
                        .build(), List.of()));
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(treeVersion));
    }

    private static IntentEvaluationDataset lockedDataset(
            IntentRoutingEvaluationDataset dataset, Long intentTreeVersionId) {
        return IntentEvaluationDataset.builder()
                .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion(dataset.getDatasetVersion())
                .applicationId(dataset.getApplicationId())
                .applicationVersionId(dataset.getApplicationVersionId())
                .intentTreeId(dataset.getIntentTreeId())
                .intentTreeVersionId(intentTreeVersionId)
                .embeddingModelInstanceId(dataset.getEmbeddingModelInstanceId())
                .leafMetadata(dataset.leafMetadata())
                .samples(List.of())
                .build();
    }

    private static IntentRoutingPreviewResult resultFor(String query) {
        int index = Integer.parseInt(query.substring("sample-".length()));
        IntentRoutingAction action = expectedAction(index);
        String intentCode = action == IntentRoutingAction.DEFER_TO_AGENT ? null : "intent-" + index;
        List<IntentRoutingPreviewCandidate> candidates = intentCode == null
                ? List.of() : List.of(new IntentRoutingPreviewCandidate(intentCode, intentCode, 0.9D, 1));
        return preview(action, intentCode, candidates);
    }

    private static IntentRoutingPreviewResult preview(
            IntentRoutingAction action, String intentCode, List<IntentRoutingPreviewCandidate> candidates) {
        IntentSemanticAction semanticAction = switch (action) {
            case CLARIFICATION -> IntentSemanticAction.CLARIFY;
            case DEFER_TO_AGENT -> IntentSemanticAction.DEFER;
            default -> IntentSemanticAction.SELECT;
        };
        return IntentRoutingPreviewResult.builder()
                .candidates(candidates)
                .retrievedCandidates(candidates)
                .semanticAction(semanticAction)
                .semanticReasonCode(semanticAction == IntentSemanticAction.DEFER
                        ? IntentDecisionReason.OUT_OF_SCOPE
                        : semanticAction == IntentSemanticAction.CLARIFY
                        ? IntentDecisionReason.MISSING_REQUIRED_SLOT
                        : IntentDecisionReason.SINGLE_INTENT)
                .selectedIntentCode(intentCode)
                .alternativeIntentCodes(List.of())
                .recognizedSlots(List.of())
                .finalAction(action)
                .finalReasonCode(action == IntentRoutingAction.DEFER_TO_AGENT
                        ? IntentRoutingReason.SEMANTIC_DEFER
                        : action == IntentRoutingAction.CLARIFICATION
                        ? IntentRoutingReason.SEMANTIC_CLARIFICATION
                        : IntentRoutingReason.ROUTED_BY_SEMANTIC)
                .modelInstanceId(51L)
                .promptVersionId(52L)
                .tokenUsage(TokenUsage.of(8, 2, null))
                .modelCalls(action == IntentRoutingAction.DEFER_TO_AGENT ? 0 : 1)
                .durationMillis(20L)
                .build();
    }

    private static IntentRoutingAction expectedAction(int index) {
        IntentEvaluationScenario scenario = IntentEvaluationScenario.values()[
                index % IntentEvaluationScenario.values().length];
        if (scenario == IntentEvaluationScenario.OUT_OF_SCOPE
                || scenario == IntentEvaluationScenario.MULTI_INTENT) {
            return IntentRoutingAction.DEFER_TO_AGENT;
        }
        IntentRoutingAction[] actions = {
                IntentRoutingAction.EXECUTE_AGENT,
                IntentRoutingAction.EXECUTE_WORKFLOW,
                IntentRoutingAction.FIXED_RESPONSE,
                IntentRoutingAction.CLARIFICATION
        };
        return actions[index % actions.length];
    }
}
