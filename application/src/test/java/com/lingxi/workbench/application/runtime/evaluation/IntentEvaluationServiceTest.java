package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.routing.IntentCandidateRetriever;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentEvaluationServiceTest {

    private final ApplicationRuntimeRepo applicationRuntimeRepo = mock(ApplicationRuntimeRepo.class);
    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo = mock(IntentTreeRuntimeRepo.class);
    private final IntentCandidateRetriever candidateRetriever = mock(IntentCandidateRetriever.class);
    private final IntentEvaluationService service = new IntentEvaluationService(
            applicationRuntimeRepo,
            intentTreeRuntimeRepo,
            candidateRetriever);

    @Test
    void shouldExportLeafMetadataFromLockedVersions() {
        RuntimeApplicationVersionEntity applicationVersion = applicationVersion();
        RuntimeIntentTreeVersionEntity intentTreeVersion = intentTreeVersion();
        when(applicationRuntimeRepo.findVersion(10L, 20L)).thenReturn(Optional.of(applicationVersion));
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(intentTreeVersion));

        IntentEvaluationDataset result = service.exportDatasetTemplate(10L, 20L, "v1-baseline-1");

        assertThat(result.getSchemaVersion()).isEqualTo(1);
        assertThat(result.getIntentTreeVersionId()).isEqualTo(40L);
        assertThat(result.leafMetadata()).extracting(IntentEvaluationLeafMetadata::getIntentCode)
                .containsExactly("complaint", "exchange", "refund");
        assertThat(result.leafMetadata().get(1).getTargetType()).isEqualTo("AGENT");
        assertThat(result.samples()).isEmpty();
    }

    @Test
    void shouldBuildComparableReportForValidBaselineDataset() {
        RuntimeApplicationVersionEntity applicationVersion = applicationVersion();
        RuntimeIntentTreeVersionEntity intentTreeVersion = intentTreeVersion();
        when(applicationRuntimeRepo.findVersion(10L, 20L)).thenReturn(Optional.of(applicationVersion));
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(intentTreeVersion));
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(
                List.of(candidate("refund", 0.92D)),
                List.of(
                        candidate("refund", 0.92D),
                        candidate("exchange", 0.70D))));

        IntentEvaluationReport report = service.evaluate(datasetWithAllScenarios());

        assertThat(report.getSampleCount()).isEqualTo(100);
        assertThat(report.getMetrics().getCandidateRecallAtK()).isEqualTo(1D);
        assertThat(report.getMetrics().getTop1Accuracy()).isEqualTo(1D);
        assertThat(report.getMetrics().getFinalActionAccuracy()).isEqualTo(1D);
        assertThat(report.getFailures()).isEmpty();
        assertThat(report.getConfusionMatrix()).anySatisfy(entry -> {
            assertThat(entry.expectedAction()).isEqualTo(IntentEvaluationExpectedAction.MATCHED);
            assertThat(entry.actualAction()).isEqualTo(IntentEvaluationExpectedAction.MATCHED);
            assertThat(entry.count()).isEqualTo(100L);
        });
        verify(candidateRetriever, times(100)).retrieve(any());
    }

    @Test
    void shouldRejectDatasetWithMissingExpectedAction() {
        IntentEvaluationDataset dataset = datasetWithAllScenarios();
        dataset.samples().getFirst().setExpectedAction(null);

        assertThatThrownBy(() -> service.evaluate(dataset))
                .isInstanceOf(BusinessException.class)
                .hasMessage("评测样本缺少expectedAction");
    }

    @Test
    void shouldRejectDatasetWithDuplicateSampleId() {
        IntentEvaluationDataset dataset = datasetWithAllScenarios();
        dataset.samples().get(1).setId(dataset.samples().getFirst().getId());

        assertThatThrownBy(() -> service.evaluate(dataset))
                .isInstanceOf(BusinessException.class)
                .hasMessage("评测样本id重复");
    }

    @Test
    void shouldRejectExpectedIntentCodeOutsideLockedVersion() {
        mockLockedVersions();
        IntentEvaluationDataset dataset = datasetWithAllScenarios();
        dataset.samples().getFirst().setExpectedIntentCodes(List.of("deleted-intent"));

        assertThatThrownBy(() -> service.evaluate(dataset))
                .isInstanceOf(BusinessException.class)
                .hasMessage("评测样本包含当前发布意图树不存在的编码");
    }

    @Test
    void shouldTreatUnexpectedAmbiguousCandidateAsFailure() {
        mockLockedVersions();
        when(candidateRetriever.retrieve(any())).thenReturn(new IntentCandidateSet(
                List.of(
                        candidate("refund", 0.92D),
                        candidate("exchange", 0.90D),
                        candidate("complaint", 0.89D)),
                List.of(
                        candidate("refund", 0.92D),
                        candidate("exchange", 0.90D),
                        candidate("complaint", 0.89D))));
        IntentEvaluationDataset dataset = datasetWithAllScenarios();
        dataset.samples().forEach(sample -> {
            sample.setExpectedAction(IntentEvaluationExpectedAction.AMBIGUOUS);
            sample.setExpectedIntentCodes(List.of("refund", "exchange"));
        });

        IntentEvaluationReport report = service.evaluate(dataset);

        assertThat(report.getMetrics().getFinalActionAccuracy()).isZero();
        assertThat(report.getFailures()).hasSize(100);
    }

    private static IntentEvaluationDataset datasetWithAllScenarios() {
        List<IntentEvaluationSample> samples = new ArrayList<>();
        IntentEvaluationScenario[] scenarios = IntentEvaluationScenario.values();
        for (int index = 0; index < 100; index++) {
            samples.add(IntentEvaluationSample.builder()
                    .id("v1-" + index)
                    .query("我要退款 " + index)
                    .scenario(scenarios[index % scenarios.length])
                    .expectedAction(IntentEvaluationExpectedAction.MATCHED)
                    .expectedIntentCodes(List.of("refund"))
                    .build());
        }
        return IntentEvaluationDataset.builder()
                .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion("v1-baseline-1")
                .applicationId(10L)
                .applicationVersionId(20L)
                .intentTreeId(30L)
                .intentTreeVersionId(40L)
                .embeddingModelInstanceId(50L)
                .samples(samples)
                .build();
    }

    private static IntentCandidate candidate(String intentCode, double similarity) {
        return IntentCandidate.builder()
                .intentCode(intentCode)
                .similarity(similarity)
                .build();
    }

    private void mockLockedVersions() {
        when(applicationRuntimeRepo.findVersion(10L, 20L)).thenReturn(Optional.of(applicationVersion()));
        when(intentTreeRuntimeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(intentTreeVersion()));
    }

    private static RuntimeApplicationVersionEntity applicationVersion() {
        RuntimeApplicationVersionEntity entity = new RuntimeApplicationVersionEntity();
        entity.setId(20L);
        entity.setApplicationId(10L);
        entity.setDefinitionSnapshot(ApplicationDefinition.builder()
                .intentRouting(ApplicationIntentBinding.builder()
                        .enabled(true)
                        .intentTreeId(30L)
                        .intentTreeVersionId(40L)
                        .mappings(List.of(
                                ApplicationIntentMapping.builder()
                                        .intentCode("refund")
                                        .targetType(IntentExecutionTargetType.WORKFLOW)
                                        .capabilityCode("refund-workflow")
                                        .targetId(100L)
                                        .targetVersionId(101L)
                                        .build(),
                                ApplicationIntentMapping.builder()
                                        .intentCode("exchange")
                                        .targetType(IntentExecutionTargetType.AGENT)
                                        .capabilityCode("exchange-agent")
                                        .targetId(102L)
                                        .targetVersionId(103L)
                                        .build()))
                        .build())
                .build());
        return entity;
    }

    private static RuntimeIntentTreeVersionEntity intentTreeVersion() {
        RuntimeIntentTreeVersionEntity entity = new RuntimeIntentTreeVersionEntity();
        entity.setId(40L);
        entity.setIntentTreeId(30L);
        entity.setEmbeddingModelInstanceId(50L);
        entity.setDefinitionSnapshot(new IntentTreeDefinition(
                2,
                50L,
                IntentRoutingPolicy.builder()
                        .topK(3)
                        .recallThreshold(new BigDecimal("0.60"))
                        .chatModelInstanceId(51L)
                        .decisionPromptVersionId(52L)
                        .maxOutputTokens(512)
                        .timeoutSeconds(30)
                        .maxRepeatAttempts(1)
                        .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                        .build(),
                List.of(
                        leaf("refund-node", "refund", "退款"),
                        leaf("exchange-node", "exchange", "换货"),
                        leaf("complaint-node", "complaint", "投诉"))));
        return entity;
    }

    private static IntentNodeDefinition leaf(String id, String code, String name) {
        return IntentNodeDefinition.builder()
                .id(id)
                .code(code)
                .name(name)
                .description(name + "处理")
                .nodeType(IntentNodeType.LEAF)
                .positiveExamples(List.of(name))
                .negativeExamples(List.of("不要" + name))
                .enabled(true)
                .build();
    }
}
